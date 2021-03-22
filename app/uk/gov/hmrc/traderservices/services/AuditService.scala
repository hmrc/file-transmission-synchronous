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

package uk.gov.hmrc.traderservices.services

import javax.inject.Inject

import com.google.inject.Singleton
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import scala.concurrent.ExecutionContext

import scala.concurrent.Future
import scala.util.Try

import play.api.libs.json._
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.traderservices.models.UploadedFile
import uk.gov.hmrc.traderservices.models.FileTransferResult
import uk.gov.hmrc.traderservices.models.FileTransferAudit
import java.time.LocalDate
import java.time.LocalTime
import play.api.Logger

object TraderServicesAuditEvent extends Enumeration {
  type TraderServicesAuditEvent = Value
  val CreateCase, UpdateCase = Value
}

@Singleton
class AuditService @Inject() (val auditConnector: AuditConnector) {

  import TraderServicesAuditEvent._
  import AuditService._

  private def auditExtendedEvent(
    event: TraderServicesAuditEvent,
    transactionName: String,
    details: JsValue
  )(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    sendExtended(createExtendedEvent(event, transactionName, details))

  private def createExtendedEvent(
    event: TraderServicesAuditEvent,
    transactionName: String,
    details: JsValue
  )(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): ExtendedDataEvent = {
    val tags = hc.toAuditTags(transactionName, request.path)
    ExtendedDataEvent(
      auditSource = "file-transmission-synchronous",
      auditType = event.toString,
      tags = tags,
      detail = details
    )
  }

  private def sendExtended(
    events: ExtendedDataEvent*
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future {
      events.foreach { event =>
        Try(auditConnector.sendExtendedEvent(event))
      }
    }

}

object AuditService {}
