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

import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.models.FileTransferRequest
import java.util.UUID
import uk.gov.hmrc.traderservices.models.FileTransferData
import uk.gov.hmrc.traderservices.models.MultiFileTransferRequest
import play.api.libs.json.Json
import play.api.libs.json.JsString

class FileTransferRequestValidationSpec extends UnitSpec {

  "FileTransferRequest.downloadUrlValidator" should {
    "accept only valid URI" in {
      FileTransferRequest.downloadUrlValidator("").isValid shouldBe false
      FileTransferRequest.downloadUrlValidator("foo:bar").isValid shouldBe false
      FileTransferRequest.downloadUrlValidator("http://foo.bar").isValid shouldBe true
    }
  }

  "FileTransferRequest" should {

    val validRequest = FileTransferRequest(
      UUID.randomUUID().toString(),
      "Risk-123",
      "Route1",
      "XYZ0123456789",
      "http://aws.amazon.com/bucket/123/file/345",
      "c76abd0403cdd3f022d67589052807edde9e4ff81111852e5d607436220bc32a",
      "test.jpg",
      "image/jpg",
      1,
      1
    )

    "have validate method accepting only valid file transfer request" in {
      FileTransferRequest.validate(FileTransferRequest("", "", "", "", "", "", "", "", 0, 0)).isValid shouldBe false
      FileTransferRequest
        .validate(validRequest)
        .isValid shouldBe true
      FileTransferRequest
        .validate(validRequest.copy(conversationId = ""))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(caseReferenceNumber = ""))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(applicationName = ""))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(applicationName = "Foo"))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(upscanReference = ""))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(downloadUrl = ""))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(downloadUrl = "foo.jpg"))
        .isValid shouldBe true
      FileTransferRequest
        .validate(validRequest.copy(downloadUrl = "http://foo.bar/baz/foo.jpg"))
        .isValid shouldBe true
      FileTransferRequest
        .validate(validRequest.copy(downloadUrl = "data:text/plain;charset=UTF-8,hello"))
        .isValid shouldBe true
      FileTransferRequest
        .validate(validRequest.copy(downloadUrl = "foo:bar"))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(checksum = ""))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(checksum = "a"))
        .isValid shouldBe false
      for (l <- 1 to 63)
        FileTransferRequest
          .validate(validRequest.copy(checksum = "a" * l))
          .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(checksum = "a" * 64))
        .isValid shouldBe true
      FileTransferRequest
        .validate(validRequest.copy(checksum = "a" * 65))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(fileName = ""))
        .isValid shouldBe false
      FileTransferRequest
        .validate(validRequest.copy(fileName = "a"))
        .isValid shouldBe true
      for (l <- 1 to 93)
        FileTransferRequest
          .validate(validRequest.copy(fileName = "a" * l))
          .isValid shouldBe true
      FileTransferRequest
        .validate(validRequest.copy(fileName = "a" * 94))
        .isValid shouldBe true
      FileTransferRequest
        .validate(validRequest.copy(fileMimeType = ""))
        .isValid shouldBe false
    }
  }

  val validData = FileTransferData(
    "XYZ0123456789",
    "http://aws.amazon.com/bucket/123/file/345",
    "c76abd0403cdd3f022d67589052807edde9e4ff81111852e5d607436220bc32a",
    "test.jpg",
    "image/jpg"
  )

  "FileTransferData" should {
    "have validate method accepting only valid file transfer data" in {
      FileTransferData.validate(FileTransferData("", "", "", "", "")).isValid shouldBe false
      FileTransferData.validate(validData).isValid shouldBe true
      FileTransferData
        .validate(validData.copy(upscanReference = ""))
        .isValid shouldBe false
      FileTransferData
        .validate(validData.copy(downloadUrl = ""))
        .isValid shouldBe false
      FileTransferData
        .validate(validData.copy(downloadUrl = "http://foo.bar/baz/foo.jpg"))
        .isValid shouldBe true
      FileTransferData
        .validate(validData.copy(downloadUrl = "data:text/plain;charset=UTF-8,hello"))
        .isValid shouldBe true
      FileTransferData
        .validate(validData.copy(downloadUrl = "foo.jpg"))
        .isValid shouldBe true
      FileTransferData
        .validate(validData.copy(downloadUrl = "foo:bar"))
        .isValid shouldBe false
      FileTransferData
        .validate(validData.copy(checksum = ""))
        .isValid shouldBe false
      FileTransferData
        .validate(validData.copy(checksum = "a"))
        .isValid shouldBe false
      for (l <- 1 to 63)
        FileTransferData
          .validate(validData.copy(checksum = "a" * l))
          .isValid shouldBe false
      FileTransferData
        .validate(validData.copy(checksum = "a" * 64))
        .isValid shouldBe true
      FileTransferData
        .validate(validData.copy(checksum = "a" * 65))
        .isValid shouldBe false
      FileTransferData
        .validate(validData.copy(fileName = ""))
        .isValid shouldBe false
      FileTransferData
        .validate(validData.copy(fileName = "a"))
        .isValid shouldBe true
      for (l <- 1 to 93)
        FileTransferData
          .validate(validData.copy(fileName = "a" * l))
          .isValid shouldBe true
      FileTransferData
        .validate(validData.copy(fileName = "a" * 94))
        .isValid shouldBe true
      FileTransferData
        .validate(validData.copy(fileMimeType = ""))
        .isValid shouldBe false
    }
  }

  "MultiFileTransferRequest" should {

    val validRequest = MultiFileTransferRequest(
      UUID.randomUUID().toString(),
      "Risk-123",
      "Route1",
      Seq(validData),
      Some("https://foo.com/bar/123"),
      Some(Json.obj("foo" -> JsString("bar")))
    )

    "have validate method accepting only valid files transfer request" in {
      MultiFileTransferRequest.validate(MultiFileTransferRequest("", "", "", Seq.empty)).isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest).isValid shouldBe true
      MultiFileTransferRequest.validate(validRequest.copy(conversationId = "")).isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest.copy(applicationName = "")).isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest.copy(applicationName = "foo")).isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest.copy(caseReferenceNumber = "")).isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest.copy(files = Seq.empty)).isValid shouldBe false
      MultiFileTransferRequest
        .validate(validRequest.copy(files = Seq(validData.copy(fileName = ""))))
        .isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest.copy(callbackUrl = Some(""))).isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest.copy(callbackUrl = Some("foo:bar"))).isValid shouldBe false
      MultiFileTransferRequest.validate(validRequest.copy(callbackUrl = Some("http://bar"))).isValid shouldBe true
    }
  }

}
