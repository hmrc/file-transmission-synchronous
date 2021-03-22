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

  // POST /transfer-file
  final val transferFile: Action[String] =
    Action.async(parseTolerantTextUtf8) { implicit request =>
      withAuthorised {
        withPayload[TraderServicesFileTransferRequest] { fileTransferRequest =>
          executeSingleFileTransfer(
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
              )
          )
        } {
          // when incoming request's payload validation fails
          case (errorCode, errorMessage) =>
            Future.successful(BadRequest)
        }
      }
        .recover {
          // last resort fallback when request processing collapses
          case e => InternalServerError
        }
    }
}
