package uk.gov.hmrc.traderservices.controllers

import java.time.LocalDateTime
import org.scalatest.Suite
import org.scalatestplus.play.ServerProvider
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.traderservices.stubs._
import uk.gov.hmrc.traderservices.support.ServerBaseISpec
import uk.gov.hmrc.traderservices.support.JsonMatchers
import com.github.tomakehurst.wiremock.http.Fault
import play.api.libs.ws.BodyWritable
import java.nio.charset.StandardCharsets
import play.api.libs.ws.InMemoryBody
import akka.util.ByteString

class FileTransferControllerISpec extends ServerBaseISpec with AuthStubs with FileTransferStubs with JsonMatchers {
  this: Suite with ServerProvider =>

  val url = s"http://localhost:$port"

  val dateTime = LocalDateTime.now()

  val wsClient = app.injector.instanceOf[WSClient]

  val emptyArray = Array.emptyByteArray
  val oneByteArray = Array.fill[Byte](1)(255.toByte)
  val twoBytesArray = Array.fill[Byte](2)(255.toByte)
  val threeBytesArray = Array.fill[Byte](3)(255.toByte)

  "FileTransferController" when {
    "POST /transfer-file" should {
      testFileTransferSuccess("emptyArray", "Route1", Some(emptyArray))
      testFileTransferSuccess("oneByteArray", "Route1", Some(oneByteArray))
      testFileTransferSuccess("twoBytesArray", "Route1", Some(twoBytesArray))
      testFileTransferSuccess("threeBytesArray", "Route1", Some(threeBytesArray))
      testFileTransferSuccess("prod.routes", "Route1")
      testFileTransferSuccess("app.routes", "Route1")
      testFileTransferSuccess("schema.json", "Route1")
      testFileTransferSuccess("logback.xml", "Route1")
      testFileTransferSuccess("test⫐1.jpeg", "Route1")
      testFileTransferSuccess("test2.txt", "Route1")

      testFileTransferSuccess("emptyArray", "NDRC", Some(emptyArray))
      testFileTransferSuccess("oneByteArray", "NDRC", Some(oneByteArray))
      testFileTransferSuccess("twoBytesArray", "NDRC", Some(twoBytesArray))
      testFileTransferSuccess("threeBytesArray", "NDRC", Some(threeBytesArray))
      testFileTransferSuccess("prod.routes", "NDRC")
      testFileTransferSuccess("app.routes", "NDRC")
      testFileTransferSuccess("schema.json", "NDRC")
      testFileTransferSuccess("logback.xml", "NDRC")
      testFileTransferSuccess("test⫐1.jpeg", "NDRC")
      testFileTransferSuccess("test2.txt", "NDRC")

      testFileUploadFailure("emptyArray", 404, Some(emptyArray))
      testFileUploadFailure("oneByteArray", 404, Some(oneByteArray))
      testFileUploadFailure("twoBytesArray", 404, Some(twoBytesArray))
      testFileUploadFailure("threeBytesArray", 404, Some(threeBytesArray))
      testFileUploadFailure("prod.routes", 500)
      testFileUploadFailure("app.routes", 404)
      testFileUploadFailure("schema.json", 501)
      testFileUploadFailure("logback.xml", 409)
      testFileUploadFailure("test⫐1.jpeg", 403)

      testFileDownloadFailure("emptyArray", 404, Some(emptyArray))
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
          .post(Json.obj())
          .futureValue

        result.status shouldBe 400
        verifyAuthorisationHasHappened()
      }

      "return 400 when malformed payload" in {
        givenAuthorised()
        val conversationId = java.util.UUID.randomUUID().toString()

        val jsonBodyWritable =
          BodyWritable
            .apply[String](s => InMemoryBody(ByteString.fromString(s, StandardCharsets.UTF_8)), "application/json")

        val result = wsClient
          .url(s"$url/transfer-file")
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
    }
  }

  def testFileTransferSuccess(fileName: String, applicationName: String, bytesOpt: Option[Array[Byte]] = None) {
    s"return 202 when transferring $fileName for #$applicationName succeeds" in new FileTransferTest(
      fileName,
      bytesOpt
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
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", applicationName)))
        .futureValue

      result.status shouldBe 202
      verifyAuthorisationHasHappened()
    }
  }

  def testFileUploadFailure(fileName: String, status: Int, bytesOpt: Option[Array[Byte]] = None) {
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
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", "Route1")))
        .futureValue

      result.status shouldBe status
      verifyAuthorisationHasHappened()
    }
  }

  def testFileDownloadFailure(fileName: String, status: Int, bytesOpt: Option[Array[Byte]] = None) {
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
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", "Route1")))
        .futureValue

      result.status shouldBe 500
      verifyAuthorisationHasHappened()
    }
  }

  def testFileDownloadFault(fileName: String, status: Int, fault: Fault) {
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
        .post(Json.parse(jsonPayload("Risk-123", "Route1")))
        .futureValue

      result.status shouldBe 500
      verifyAuthorisationHasHappened()
    }
  }

}
