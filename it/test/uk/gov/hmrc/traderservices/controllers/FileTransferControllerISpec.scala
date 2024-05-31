/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.http.Fault
import org.apache.pekko.util.ByteString
import org.scalatest.Suite
import org.scalatestplus.play.ServerProvider
import play.api.libs.json.Json
import play.api.libs.ws.{BodyWritable, InMemoryBody, WSClient}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.traderservices.models.FileTransferRequest
import uk.gov.hmrc.traderservices.stubs._
import uk.gov.hmrc.traderservices.support.{JsonMatchers, ServerBaseISpec}
import uk.gov.hmrc.traderservices.utilities.RealUUIDGenerator

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class FileTransferControllerISpec extends ServerBaseISpec with AuthStubs with FileTransferStubs with JsonMatchers {
  this: Suite with ServerProvider =>

  val url = s"http://localhost:$port"

  val dateTime = LocalDateTime.now()

  val wsClient = app.injector.instanceOf[WSClient]

  val oneByteArray = Array.fill[Byte](1)(255.toByte)
  val twoBytesArray = Array.fill[Byte](2)(255.toByte)
  val threeBytesArray = Array.fill[Byte](3)(255.toByte)

  "FileTransferController" when {
    "POST /transfer-file" should {
      testFileTransferBadRequest("request with an empty conversationId", exampleRequest.copy(conversationId = ""))
      testFileTransferBadRequest("request with an empty correlationId", exampleRequest.copy(correlationId = Some("")))
      testFileTransferBadRequest("request with an empty applicationName", exampleRequest.copy(applicationName = ""))
      testFileTransferBadRequest("request with invalid applicationName", exampleRequest.copy(applicationName = "FOO"))
      testFileTransferBadRequest("request with an empty upscanReference", exampleRequest.copy(upscanReference = ""))
      testFileTransferBadRequest("request with an empty downloadUrl", exampleRequest.copy(downloadUrl = ""))
      testFileTransferBadRequest("request with an empty checksum", exampleRequest.copy(checksum = ""))
      testFileTransferBadRequest("request with too long checksum", exampleRequest.copy(checksum = "a" * 65))
      testFileTransferBadRequest("request with an empty fileName", exampleRequest.copy(fileName = ""))
      testFileTransferBadRequest("request with an empty fileMimeType", exampleRequest.copy(fileMimeType = ""))
      testFileTransferBadRequest("request with a zero batchSize", exampleRequest.copy(batchSize = 0))
      testFileTransferBadRequest("request with a zero batchCount", exampleRequest.copy(batchCount = 0))
      testFileTransferBadRequest(
        "request with a batchCount greater than batchSize",
        exampleRequest.copy(batchCount = 2, batchSize = 1)
      )

      testFileTransferSuccess("oneByteArray", "Route1", Some(oneByteArray))
      testFileTransferSuccess("twoBytesArray", "Route1", Some(twoBytesArray))
      testFileTransferSuccess("threeBytesArray", "Route1", Some(threeBytesArray))
      testFileTransferSuccess("prod.routes", "Route1")
      testFileTransferSuccess("app.routes", "Route1")
      testFileTransferSuccess("schema.json", "Route1")
      testFileTransferSuccess("logback.xml", "Route1")
      testFileTransferSuccess("test⫐1.jpeg", "Route1")
      testFileTransferSuccess("test2.txt", "Route1")

      testDataTransferSuccess("oneByteArray", "Route1", Some(oneByteArray))
      testDataTransferSuccess("twoBytesArray", "Route1", Some(twoBytesArray))
      testDataTransferSuccess("threeBytesArray", "Route1", Some(threeBytesArray))
      testDataTransferSuccess("prod.routes", "Route1")
      testDataTransferSuccess("app.routes", "Route1")
      testDataTransferSuccess("schema.json", "Route1")
      testDataTransferSuccess("logback.xml", "Route1")
      testDataTransferSuccess("test⫐1.jpeg", "Route1")
      testDataTransferSuccess("test2.txt", "Route1")

      testFileTransferSuccess("oneByteArray", "NDRC", Some(oneByteArray))
      testFileTransferSuccess("twoBytesArray", "NDRC", Some(twoBytesArray))
      testFileTransferSuccess("threeBytesArray", "NDRC", Some(threeBytesArray))
      testFileTransferSuccess("prod.routes", "NDRC")
      testFileTransferSuccess("app.routes", "NDRC")
      testFileTransferSuccess("schema.json", "NDRC")
      testFileTransferSuccess("logback.xml", "NDRC")
      testFileTransferSuccess("test⫐1.jpeg", "NDRC")
      testFileTransferSuccess("test2.txt", "NDRC")

      testFileUploadFailure("oneByteArray", 404, Some(oneByteArray))
      testFileUploadFailure("twoBytesArray", 404, Some(twoBytesArray))
      testFileUploadFailure("threeBytesArray", 404, Some(threeBytesArray))
      testFileUploadFailure("prod.routes", 500)
      testFileUploadFailure("app.routes", 404)
      testFileUploadFailure("schema.json", 501)
      testFileUploadFailure("logback.xml", 409)
      testFileUploadFailure("test⫐1.jpeg", 403)

      testFileDownloadFailure("oneByteArray", 404, Some(oneByteArray))
      testFileDownloadFailure("twoBytesArray", 404, Some(twoBytesArray))
      testFileDownloadFailure("threeBytesArray", 404, Some(threeBytesArray))
      testFileDownloadFailure("prod.routes", 400)
      testFileDownloadFailure("app.routes", 403)
      testFileDownloadFailure("schema.json", 500)
      testFileDownloadFailure("logback.xml", 501)
      testFileDownloadFailure("test⫐1.jpeg", 404)

      testFileDownloadFault("test⫐1.jpeg", 200, Fault.RANDOM_DATA_THEN_CLOSE)
      testFileDownloadFault("test2.txt", 500, Fault.RANDOM_DATA_THEN_CLOSE)
      testFileDownloadFault("test⫐1.jpeg", 200, Fault.MALFORMED_RESPONSE_CHUNK)
      testFileDownloadFault("test2.txt", 500, Fault.MALFORMED_RESPONSE_CHUNK)
      testFileDownloadFault("test⫐1.jpeg", 200, Fault.CONNECTION_RESET_BY_PEER)
      testFileDownloadFault("test2.txt", 500, Fault.CONNECTION_RESET_BY_PEER)
      testFileDownloadFault("test⫐1.jpeg", 200, Fault.EMPTY_RESPONSE)
      testFileDownloadFault("test2.txt", 500, Fault.EMPTY_RESPONSE)

      "return 400 when empty payload" in {
        givenAuthorised()

        val result = wsClient
          .url(s"$url/transfer-file")
          .withHttpHeaders(HeaderNames.authorisation -> "Bearer dummy-it-token")
          .post(Json.obj())
          .futureValue

        result.status shouldBe 400
        verifyAuthorisationHasHappened()
      }

      "return 400 when malformed payload" in {
        givenAuthorised()
        val conversationId = RealUUIDGenerator.generate()

        val jsonBodyWritable =
          BodyWritable
            .apply[String](s => InMemoryBody(ByteString.fromString(s, StandardCharsets.UTF_8)), "application/json")

        val payload = Json.obj(
          "conversationId"      -> conversationId,
          "caseReferenceNumber" -> "Risk-123",
          "applicationName"     -> "Route1",
          "upscanReference"     -> "XYZ0123456789",
          "fileName"            -> "foo",
          "fileMimeType"        -> "image/"
        )

        val result = wsClient
          .url(s"$url/transfer-file")
          .withHttpHeaders(HeaderNames.authorisation -> "Bearer dummy-it-token")
          .post(s"""{
                   |"conversationId":"$conversationId",
                   |"caseReferenceNumber":"Risk-123",
                   |"applicationName":"Route1",
                   |"upscanReference":"XYZ0123456789",
                   |"fileName":"foo",
                   |"fileMimeType":"image/""")(jsonBodyWritable)
          .futureValue

        result.status shouldBe 400
        verifyAuthorisationHasHappened()
      }

      "return 500 when authorisation fails" in new FileTransferTest(
        "foo.jpeg",
        Some(oneByteArray)
      ) {
        givenAuthorisationFails(403)
        val fileUrl =
          givenFileTransferSucceeds(
            "Risk-123",
            "Route1",
            "foo.jpeg",
            bytes,
            base64Content,
            checksum,
            fileSize,
            xmlMetadataHeader
          )

        val result = wsClient
          .url(s"$url/transfer-file")
          .withHttpHeaders("x-correlation-id" -> correlationId, HeaderNames.authorisation -> "Bearer dummy-it-token")
          .post(Json.parse(jsonPayload("Risk-123", "Route1")))
          .futureValue

        result.status shouldBe 500
        result.json should haveProperty[String]("errorCode", be("ERROR_UNKNOWN"))
        verifyAuthorisationHasHappened()
        verifyFileDownloadHaveNotHappen()
        verifyFileUploadHaveNotHappen()
      }

    }
  }

  def testFileTransferSuccess(fileName: String, applicationName: String, bytesOpt: Option[Array[Byte]] = None): Unit =
    s"return 202 when transferring $fileName for #$applicationName succeeds" in new FileTransferTest(
      fileName,
      bytesOpt,
      applicationName,
      RealUUIDGenerator
    ) {
      givenAuthorised()
      val fileUrl =
        givenFileTransferSucceeds(
          "Risk-123",
          applicationName,
          fileName,
          bytes,
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader
        )

      val result = wsClient
        .url(s"$url/transfer-file")
        .withHttpHeaders("x-correlation-id" -> correlationId, HeaderNames.authorisation -> "Bearer dummy-it-token")
        .post(Json.parse(jsonPayload("Risk-123", applicationName)))
        .futureValue

      result.status shouldBe 202
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHasHappened(1)
    }

  def testDataTransferSuccess(fileName: String, applicationName: String, bytesOpt: Option[Array[Byte]] = None): Unit =
    s"return 202 when transferring data as $fileName for #$applicationName succeeds" in new FileTransferTest(
      fileName,
      bytesOpt,
      applicationName,
      RealUUIDGenerator
    ) {
      givenAuthorised()
      val fileUrl =
        givenFileTransferSucceeds(
          "Risk-123",
          applicationName,
          fileName,
          bytes,
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader
        )

      val result = wsClient
        .url(s"$url/transfer-file")
        .withHttpHeaders("x-correlation-id" -> correlationId, HeaderNames.authorisation -> "Bearer dummy-it-token")
        .post(Json.parse(jsonDataPayload("Risk-123", applicationName)))
        .futureValue

      result.status shouldBe 202
      verifyAuthorisationHasHappened()
      verifyFileDownloadHaveNotHappen()
      verifyFileUploadHasHappened(1)
    }

  def testFileTransferBadRequest(description: String, fileTransferRequest: FileTransferRequest): Unit =
    s"return 400 when processing $description" in new FileTransferTest(
      fileTransferRequest.fileName,
      Some(oneByteArray)
    ) {
      givenAuthorised()
      val fileUrl = "https://test.com/123"

      val result = wsClient
        .url(s"$url/transfer-file")
        .withHttpHeaders("x-correlation-id" -> correlationId, HeaderNames.authorisation -> "Bearer dummy-it-token")
        .post(Json.toJson(fileTransferRequest))
        .futureValue

      result.status shouldBe 400
      verifyAuthorisationHasHappened()
      verifyFileDownloadHaveNotHappen(fileTransferRequest.fileName)
      verifyFileUploadHaveNotHappen()
    }

  def testFileUploadFailure(fileName: String, status: Int, bytesOpt: Option[Array[Byte]] = None): Unit =
    s"return 500 when uploading $fileName fails because of $status" in new FileTransferTest(fileName, bytesOpt) {
      givenAuthorised()
      val fileUrl =
        givenFileUploadFails(
          status,
          "Risk-123",
          "Route1",
          fileName,
          bytes,
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader
        )

      val result = wsClient
        .url(s"$url/transfer-file")
        .withHttpHeaders("x-correlation-id" -> correlationId, HeaderNames.authorisation -> "Bearer dummy-it-token")
        .post(Json.parse(jsonPayload("Risk-123", "Route1")))
        .futureValue

      result.status shouldBe status
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHasHappened(1)
    }

  def testFileDownloadFailure(fileName: String, status: Int, bytesOpt: Option[Array[Byte]] = None): Unit =
    s"return 500 when downloading $fileName fails because of $status" in new FileTransferTest(fileName, bytesOpt) {
      givenAuthorised()
      val fileUrl =
        givenFileDownloadFails(
          status,
          "Risk-123",
          "Route1",
          fileName,
          s"This is an expected error requested by the test, no worries.",
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader
        )

      val result = wsClient
        .url(s"$url/transfer-file")
        .withHttpHeaders("x-correlation-id" -> correlationId, HeaderNames.authorisation -> "Bearer dummy-it-token")
        .post(Json.parse(jsonPayload("Risk-123", "Route1")))
        .futureValue

      result.status shouldBe 500
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHaveNotHappen()
    }

  def testFileDownloadFault(fileName: String, status: Int, fault: Fault): Unit =
    s"return 500 when downloading $fileName fails because of $status with $fault" in new FileTransferTest(fileName) {
      givenAuthorised()
      val fileUrl =
        givenFileDownloadFault(
          status,
          fault,
          "Risk-123",
          "Route1",
          fileName,
          bytes,
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader
        )

      val result = wsClient
        .url(s"$url/transfer-file")
        .withHttpHeaders(HeaderNames.authorisation -> "Bearer dummy-it-token")
        .post(Json.parse(jsonPayload("Risk-123", "Route1")))
        .futureValue

      result.status shouldBe 500
      verifyAuthorisationHasHappened()
      verifyFileUploadHaveNotHappen()
    }

}
