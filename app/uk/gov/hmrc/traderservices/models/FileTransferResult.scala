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

package uk.gov.hmrc.traderservices.models

import play.api.libs.json.Format
import play.api.libs.json.Json
import java.time.LocalDateTime

final case class FileTransferResult(
  upscanReference: String,
  checksum: String,
  fileName: String,
  fileMimeType: String,
  fileSize: Int,
  success: Boolean,
  httpStatus: Int,
  transferredAt: LocalDateTime,
  correlationId: String,
  durationMillis: Int,
  error: Option[String] = None
)

object FileTransferResult {
  implicit val formats: Format[FileTransferResult] =
    Json.format[FileTransferResult]

  val empty =
    FileTransferResult("", "", "", "", 0, false, 0, LocalDateTime.now(), "", 0)
}
