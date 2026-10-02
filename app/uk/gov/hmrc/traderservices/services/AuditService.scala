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
import uk.gov.hmrc.traderservices.models.MultiFileTransferRequest
import uk.gov.hmrc.traderservices.models.FileTransferResult
import uk.gov.hmrc.traderservices.models.FileTransferAudit

object FileTransmissionAuditEvent extends Enumeration {
  type FileTransmissionAuditEvent = Value
  val SingleFile, MultipleFiles = Value
}

@Singleton
class AuditService @Inject() (val auditConnector: AuditConnector) {

  import FileTransmissionAuditEvent._
  import AuditService._

  final def auditMultipleFilesTransmission(request: MultiFileTransferRequest)(
    results: Seq[FileTransferResult]
  )(implicit hc: HeaderCarrier, r: Request[Any], ec: ExecutionContext): Future[Unit] = {
    val details: JsValue =
      MultiFileTransferAuditEventDetails.from(request, results)
    auditExtendedEvent(MultipleFiles, "multiple-files", details)
  }

  private def auditExtendedEvent(
    event: FileTransmissionAuditEvent,
    transactionName: String,
    details: JsValue
  )(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    sendExtended(createExtendedEvent(event, transactionName, details))

  private def createExtendedEvent(
    event: FileTransmissionAuditEvent,
    transactionName: String,
    details: JsValue
  )(implicit hc: HeaderCarrier, request: Request[Any]): ExtendedDataEvent = {
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

object AuditService {

  case class MultiFileTransferAuditEventDetails(
    success: Boolean,
    conversationId: String,
    caseReferenceNumber: String,
    applicationName: String,
    callbackUrl: Option[String],
    numberOfFiles: Int,
    successCount: Int,
    failureCount: Int,
    files: Seq[FileTransferAudit],
    metadata: Option[JsObject]
  )

  object MultiFileTransferAuditEventDetails {

    implicit val formats: Format[MultiFileTransferAuditEventDetails] =
      Json.format[MultiFileTransferAuditEventDetails]

    def from(fileTransferRequest: MultiFileTransferRequest, results: Seq[FileTransferResult]): JsValue = {
      val success = true
      Json.toJson(
        MultiFileTransferAuditEventDetails(
          success = success,
          conversationId = fileTransferRequest.conversationId,
          caseReferenceNumber = fileTransferRequest.caseReferenceNumber,
          applicationName = fileTransferRequest.applicationName,
          callbackUrl = fileTransferRequest.callbackUrl,
          numberOfFiles = fileTransferRequest.files.size,
          successCount = results.count(_.success),
          failureCount = results.count(r => !r.success),
          files = convertFileTransferResults(fileTransferRequest, results),
          metadata = fileTransferRequest.metadata
        )
      )
    }

  }

  def convertFileTransferResults(
    fileTransferRequest: MultiFileTransferRequest,
    fileTransferResults: Seq[FileTransferResult]
  ): Seq[FileTransferAudit] =
    fileTransferRequest.files.map { file =>
      val transferResultOpt = fileTransferResults.find(_.upscanReference == file.upscanReference)
      FileTransferAudit(
        upscanReference = file.upscanReference,
        downloadUrl = file.downloadUrl,
        checksum = file.checksum,
        fileName = file.fileName,
        fileMimeType = file.fileMimeType,
        transferSuccess = transferResultOpt.map(_.success).orElse(Some(false)),
        transferHttpStatus = transferResultOpt.map(_.httpStatus),
        transferredAt = transferResultOpt.map(_.transferredAt),
        transferError = transferResultOpt.flatMap(_.error)
      )
    }

}
