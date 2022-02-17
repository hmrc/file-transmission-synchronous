/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.models

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri
import play.api.libs.json.Format
import play.api.libs.json.Json

import scala.util.Try

case class FileTransferRequest(
  conversationId: String,
  caseReferenceNumber: String,
  applicationName: String,
  upscanReference: String,
  downloadUrl: String,
  checksum: String,
  fileName: String,
  fileMimeType: String,
  batchSize: Int,
  batchCount: Int,
  correlationId: Option[String] = None,
  // private field, this value will be overwritten
  // with UUID in the controller
  requestId: Option[String] = None,
  fileSize: Option[Int] = None,
  attempt: Option[Int] = None
) {
  lazy val startTime: Long = System.nanoTime()
  lazy val endTime: Long = System.nanoTime()
  def durationMillis: Int =
    ((endTime - startTime) / 1000000).toInt

  def isDataURL: Boolean =
    downloadUrl.startsWith("data:")

}

object FileTransferRequest {

  final val allowedApplicationNames = Seq("Route1", "NDRC", "C18", "FAS")

  implicit val formats: Format[FileTransferRequest] =
    Json.format[FileTransferRequest]

  import Validator._

  val caseReferenceNumberValidator: Validate[String] =
    check(
      _.lengthMinMaxInclusive(1, 32),
      s""""Invalid caseReferenceNumber, must be between 1 and 32 (inclusive) character long"""
    )

  final val conversationIdValidator: Validate[String] =
    check(
      _.lengthMinMaxInclusive(1, 36),
      "Invalid conversationId, must be between 1 and 36 (inclusive) character long"
    )

  final val correlationIdValidator: Validate[String] =
    check(
      _.lengthMinMaxInclusive(36, 36),
      "Invalid correlationId, must be 36 characters long"
    )

  final val applicationNameValidator: Validate[String] =
    check(
      _.isOneOf(allowedApplicationNames),
      s"Invalid applicationName, must be one of ${allowedApplicationNames.mkString(", ")}"
    )

  final val upscanReferenceValidator: Validate[String] =
    check(
      _.nonEmpty,
      s"Invalid upscanReference, must not be empty"
    )

  final val downloadUrlValidator: Validate[String] =
    check(
      uri => Try(HttpRequest.verifyUri(Uri(uri))).isSuccess || uri.startsWith("data:"),
      s"Invalid downloadUrl, must be a valid http(s): or data: URL"
    )

  final val checksumValidator: Validate[String] =
    check(
      _.length == 64,
      s"Invalid checksum SHA-256, must be 64 characters long"
    )

  final val fileNameValidator: Validate[String] =
    check(
      fileName => fileName.nonEmpty,
      s"Invalid fileName, must not be empty"
    )

  final val fileMimeTypeValidator: Validate[String] =
    check(
      _.nonEmpty,
      s"Invalid fileMimeType, must not be empty"
    )

  final val batchCountValidator: Validate[Int] =
    check(
      _ > 0,
      s"Invalid batchCount, must be greater than zero"
    )

  final val batchSizeValidator: Validate[Int] =
    check(
      _ > 0,
      s"Invalid batchSize, must be greater than zero"
    )

  final val fileSizeValidator: Validate[Int] =
    check(
      fileSize => fileSize > 0 && fileSize <= (6 * 1024 * 1024),
      s"Invalid fileSize, must be greater than zero and less or equal to 6 MB"
    )

  implicit val validate: Validator.Validate[FileTransferRequest] =
    Validator(
      checkProperty(_.caseReferenceNumber, caseReferenceNumberValidator),
      checkProperty(_.conversationId, conversationIdValidator),
      checkIfSome(_.correlationId, correlationIdValidator),
      checkProperty(_.applicationName, applicationNameValidator),
      checkProperty(_.upscanReference, upscanReferenceValidator),
      checkProperty(_.downloadUrl, downloadUrlValidator),
      checkProperty(_.checksum, checksumValidator),
      checkProperty(_.fileName, fileNameValidator),
      checkProperty(_.fileMimeType, fileMimeTypeValidator),
      checkProperty(_.batchCount, batchCountValidator),
      checkProperty(_.batchSize, batchSizeValidator),
      checkIfSome(_.fileSize, fileSizeValidator),
      check(
        r => r.batchCount <= r.batchSize,
        "Invalid request, batchCount must be equal or less than batchSize"
      )
    )
}
