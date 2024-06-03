/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.traderservices.controllers
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AuditService
import uk.gov.hmrc.traderservices.wiring.AppConfig
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.traderservices.connectors.MicroserviceAuthConnector
import org.apache.pekko.actor.ActorSystem
import java.util.UUID
import org.apache.pekko.stream.Materializer
import play.api.Logger
import uk.gov.hmrc.traderservices.connectors.ApiError
import play.api.libs.json.Json
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import org.apache.pekko.actor.Props

@Singleton
class FileTransferController @Inject() (
  val authConnector: MicroserviceAuthConnector,
  val env: Environment,
  val appConfig: AppConfig,
  val auditService: AuditService,
  cc: ControllerComponents
)(implicit
  val configuration: Configuration,
  ec: ExecutionContext,
  val actorSystem: ActorSystem,
  val materializer: Materializer
) extends BackendController(cc) with AuthActions with ControllerHelper with FileTransferFlow
    with MultiFileTransferCallbackFlow {

  val unitInterval = appConfig.unitInterval

  val correlationId = "541216ee-1926-4f1a-8e25-0d6c56ea11e9"

  // POST /transfer-file
  final val transferFile: Action[String] =
    Action.async(parseTolerantTextUtf8) { implicit request =>
      withAuthorised {
        withPayload[FileTransferRequest] { fileTransferRequest =>
          executeSingleFileTransfer[Result](
            fileTransferRequest
              .copy(
                correlationId = Some(
                  fileTransferRequest.correlationId
                    .orElse(request.headers.get("X-Correlation-Id"))
                    .getOrElse(correlationId)
                ),
                requestId = hc.requestId.map(_.value)
              ),
            (httpStatus: Int, httpBody: Option[String], fileTransferRequest: FileTransferRequest) =>
              Status(httpStatus)(httpBody.getOrElse("")),
            (error: Throwable, fileTransferRequest: FileTransferRequest) =>
              InternalServerError(
                Json
                  .toJson(
                    ApiError("ERROR_FILE_DOWNLOAD", Option(s"${error.getClass().getName()}: ${error.getMessage()}"))
                  )
              ),
            Ok
          )
        } {
          // when incoming request's payload validation fails
          case (errorCode, errorMessage) =>
            Logger(getClass).error(s"$errorCode $errorMessage")
            Future.successful(
              BadRequest(Json.toJson(ApiError(errorCode, Some(errorMessage))))
            )
        }
      }
        .recover {
          // last resort fallback when request processing collapses
          case e: Throwable =>
            Logger(getClass).error(s"${e.getClass().getName()}: ${e.getMessage()}")
            InternalServerError(
              Json.toJson(ApiError("ERROR_UNKNOWN", Option(s"${e.getClass().getName()}: ${e.getMessage()}")))
            )
        }
    }

  final val transferFunction: FileTransferActor.TransferFunction =
    executeSingleFileTransfer[FileTransferActor.TransferResult]

  // POST /transfer-multiple-files
  final val transferMultipleFiles: Action[String] =
    Action.async(parseTolerantTextUtf8) { implicit request =>
      withAuthorised {
        withPayload[MultiFileTransferRequest] { fileTransferRequest =>
          val requestId: String = UUID.randomUUID().toString()

          val auditFunction: FileTransferActor.AuditFunction =
            auditService.auditMultipleFilesTransmission(fileTransferRequest)

          val callbackFunction: FileTransferActor.CallbackFunction =
            (result: MultiFileTransferResult) =>
              fileTransferRequest.callbackUrl match {
                case None =>
                  Future.successful(Right(()))

                case Some(callbackUrl) =>
                  executeCallback(callbackUrl, result)
              }

          // Single-use actor responsible for transferring files batch to PEGA
          val fileTransferActor: ActorRef =
            actorSystem.actorOf(
              Props(
                classOf[FileTransferActor],
                fileTransferRequest.conversationId,
                fileTransferRequest.caseReferenceNumber,
                fileTransferRequest.applicationName,
                fileTransferRequest.metadata,
                requestId,
                transferFunction,
                auditFunction,
                callbackFunction,
                unitInterval
              )
            )

          val msg = FileTransferActor.TransferMultipleFiles(
            fileTransferRequest.files.zipWithIndex,
            fileTransferRequest.files.size
          )

          if (fileTransferRequest.shouldCallbackAsync) {
            fileTransferActor ! msg
            Future.successful(Accepted)
          } else
            fileTransferActor
              .ask(msg)(Timeout(5, java.util.concurrent.TimeUnit.MINUTES))
              .mapTo[MultiFileTransferResult]
              .map(result => Created(Json.toJson(result)))
        } {
          // when incoming request's payload validation fails
          case (errorCode, errorMessage) =>
            Logger(getClass).error(s"$errorCode $errorMessage")
            Future.successful(
              BadRequest(Json.toJson(ApiError(errorCode, Some(errorMessage))))
            )
        }
      }
        .recover {
          // last resort fallback when request processing collapses
          case e: Throwable =>
            Logger(getClass).error(s"${e.getClass().getName()}: ${e.getMessage()}")
            InternalServerError(
              Json.toJson(ApiError("ERROR_UNKNOWN", Option(s"${e.getClass().getName()}: ${e.getMessage()}")))
            )
        }
    }
}
