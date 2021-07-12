/*
 * Copyright 2021 HM Revenue & Customs
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
import akka.actor.ActorSystem
import java.util.UUID
import akka.stream.Materializer
import play.api.Logger
import uk.gov.hmrc.traderservices.connectors.ApiError
import play.api.libs.json.Json
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.Props
import akka.http.scaladsl.model.HttpResponse

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
) extends BackendController(cc) with AuthActions with ControllerHelper with FileTransferFlow {

  val httpResponseProcessingTimeout =
    appConfig.httpResponseProcessingTimeout

  // POST /transfer-file
  final val transferFile: Action[String] =
    Action.async(parseTolerantTextUtf8) { implicit request =>
      withAuthorised {
        withPayload[FileTransferRequest] { fileTransferRequest =>
          executeSingleFileTransfer[Result](
            fileTransferRequest
              .copy(
                correlationId = fileTransferRequest.correlationId
                  .orElse(request.headers.get("X-Correlation-Id"))
                  .orElse(
                    request.headers
                      .get("X-Request-Id")
                      .map(_.takeRight(36))
                  )
                  .orElse(Some(UUID.randomUUID().toString())),
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
              BadRequest(Json.toJson(ApiError(errorCode, if (errorMessage.isEmpty()) None else Some(errorMessage))))
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

  final val multiFileTransferFunction: FileTransferActor.TransferFunction =
    executeSingleFileTransfer[FileTransferResult]

  // POST /transfer-multiple-files
  final val transferMultipleFiles: Action[String] =
    Action.async(parseTolerantTextUtf8) { implicit request =>
      withAuthorised {
        withPayload[MultiFileTransferRequest] { fileTransferRequest =>
          val requestId: String = request.headers
            .get("X-Request-Id")
            .map(_.takeRight(36))
            .getOrElse(UUID.randomUUID().toString())

          val audit: FileTransferActor.AuditFunction =
            auditService.auditMultipleFilesTransmission(fileTransferRequest)

          // Single-use actor responsible for transferring files batch to PEGA
          val fileTransferActor: ActorRef =
            actorSystem.actorOf(
              Props(
                classOf[FileTransferActor],
                fileTransferRequest.conversationId,
                fileTransferRequest.caseReferenceNumber,
                fileTransferRequest.applicationName,
                requestId,
                multiFileTransferFunction,
                audit
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
              .mapTo[Seq[FileTransferResult]]
              .map(results => Created(Json.toJson(MultiFileTransferResult(results))))
        } {
          // when incoming request's payload validation fails
          case (errorCode, errorMessage) =>
            Logger(getClass).error(s"$errorCode $errorMessage")
            Future.successful(
              BadRequest(Json.toJson(ApiError(errorCode, if (errorMessage.isEmpty()) None else Some(errorMessage))))
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
