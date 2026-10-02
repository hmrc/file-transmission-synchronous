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

package uk.gov.hmrc.traderservices.models

import play.api.libs.json.Json
import play.api.libs.json.Format
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.HttpRequest
import scala.util.Try
import play.api.libs.json.JsObject

case class MultiFileTransferRequest(
  conversationId: String,
  caseReferenceNumber: String,
  applicationName: String,
  files: Seq[FileTransferData],
  callbackUrl: Option[String] = None,
  metadata: Option[JsObject] = None
) {

  def shouldCallbackAsync: Boolean =
    callbackUrl.isDefined

}

case class FileTransferData(
  upscanReference: String,
  downloadUrl: String,
  checksum: String,
  fileName: String,
  fileMimeType: String,
  fileSize: Option[Int] = None
)

object FileTransferData {

  import FileTransferRequest._
  import Validator._

  implicit val formats: Format[FileTransferData] =
    Json.format[FileTransferData]

  implicit val validate: Validator.Validate[FileTransferData] =
    Validator(
      checkProperty(_.upscanReference, upscanReferenceValidator),
      checkProperty(_.downloadUrl, downloadUrlValidator),
      checkProperty(_.checksum, checksumValidator),
      checkProperty(_.fileName, fileNameValidator),
      checkProperty(_.fileMimeType, fileMimeTypeValidator),
      checkIfSome(_.fileSize, fileSizeValidator)
    )
}

object MultiFileTransferRequest {

  import FileTransferRequest._
  import Validator._

  implicit val formats: Format[MultiFileTransferRequest] =
    Json.format[MultiFileTransferRequest]

  final val callbackUrlValidator: Validate[String] =
    Validator(
      check(
        _.nonEmpty,
        s"Invalid callbackUrl, must not be empty if defined"
      ),
      check(
        url => Try(HttpRequest.verifyUri(Uri(url))).isSuccess,
        s"Invalid callbackUrl, must have valid URL syntax"
      )
    )

  implicit val validate: Validator.Validate[MultiFileTransferRequest] =
    Validator(
      checkProperty(_.caseReferenceNumber, caseReferenceNumberValidator),
      checkProperty(_.conversationId, conversationIdValidator),
      checkProperty(_.applicationName, applicationNameValidator),
      check(_.files.nonEmpty, "List of files to transfer must not be empty"),
      checkEach(_.files, FileTransferData.validate),
      checkIfSome(_.callbackUrl, callbackUrlValidator)
    )
}
