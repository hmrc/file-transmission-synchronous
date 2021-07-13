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
import uk.gov.hmrc.traderservices.models.MultiFileTransferRequest
import uk.gov.hmrc.traderservices.models.FileTransferResult
import uk.gov.hmrc.traderservices.models.MultiFileTransferResult
import uk.gov.hmrc.traderservices.services.FileTransmissionAuditEvent

class MultiFileTransferControllerISpec
    extends ServerBaseISpec with AuthStubs with MultiFileTransferStubs with JsonMatchers {
  this: Suite with ServerProvider =>

  val url = s"http://localhost:$port"

  val dateTime = LocalDateTime.now()

  val wsClient = app.injector.instanceOf[WSClient]

  val emptyArray = Array.emptyByteArray
  val oneByteArray = Array.fill[Byte](1)(255.toByte)
  val twoBytesArray = Array.fill[Byte](2)(255.toByte)
  val threeBytesArray = Array.fill[Byte](3)(255.toByte)

  "MultiFileTransferController" when {
    "POST /transfer-multiple-files" should {
      testFileTransferBadRequest(
        "request with an empty conversationId",
        exampleMultiFileRequest.copy(conversationId = "")
      )
      testFileTransferBadRequest(
        "request with an empty applicationName",
        exampleMultiFileRequest.copy(applicationName = "")
      )
      testFileTransferBadRequest(
        "request with invalid applicationName",
        exampleMultiFileRequest.copy(applicationName = "FOO")
      )

      for (applicationName <- Seq("Route1", "NDRC", "NIDAC")) {
        testSingleFileTransferSuccessWithoutCallback("emptyArray", applicationName, Some(emptyArray))
        testSingleFileTransferSuccessWithoutCallback("oneByteArray", applicationName, Some(oneByteArray))
        testSingleFileTransferSuccessWithoutCallback("twoBytesArray", applicationName, Some(twoBytesArray))
        testSingleFileTransferSuccessWithoutCallback("threeBytesArray", applicationName, Some(threeBytesArray))
        testSingleFileTransferSuccessWithoutCallback("prod.routes", applicationName)
        testSingleFileTransferSuccessWithoutCallback("app.routes", applicationName)
        testSingleFileTransferSuccessWithoutCallback("schema.json", applicationName)
        testSingleFileTransferSuccessWithoutCallback("logback.xml", applicationName)
        testSingleFileTransferSuccessWithoutCallback("test⫐1.jpeg", applicationName)
        testSingleFileTransferSuccessWithoutCallback("test2.txt", applicationName)
      }

      testSingleFileUploadFailureWithoutCallback("emptyArray", 404, Some(emptyArray))
      testSingleFileUploadFailureWithoutCallback("oneByteArray", 404, Some(oneByteArray))
      testSingleFileUploadFailureWithoutCallback("twoBytesArray", 404, Some(twoBytesArray))
      testSingleFileUploadFailureWithoutCallback("threeBytesArray", 404, Some(threeBytesArray))
      testSingleFileUploadFailureWithoutCallback("prod.routes", 500)
      testSingleFileUploadFailureWithoutCallback("app.routes", 404)
      testSingleFileUploadFailureWithoutCallback("schema.json", 501)
      testSingleFileUploadFailureWithoutCallback("logback.xml", 409)
      testSingleFileUploadFailureWithoutCallback("test⫐1.jpeg", 403)

      testSingleFileDownloadFailureWithoutCallback("emptyArray", 404, Some(emptyArray))
      testSingleFileDownloadFailureWithoutCallback("oneByteArray", 404, Some(oneByteArray))
      testSingleFileDownloadFailureWithoutCallback("twoBytesArray", 404, Some(twoBytesArray))
      testSingleFileDownloadFailureWithoutCallback("threeBytesArray", 404, Some(threeBytesArray))
      testSingleFileDownloadFailureWithoutCallback("prod.routes", 400)
      testSingleFileDownloadFailureWithoutCallback("app.routes", 403)
      testSingleFileDownloadFailureWithoutCallback("schema.json", 500)
      testSingleFileDownloadFailureWithoutCallback("logback.xml", 501)
      testSingleFileDownloadFailureWithoutCallback("test⫐1.jpeg", 404)

      testSingleFileDownloadFaultWithoutCallback("test⫐1.jpeg", 200, Fault.RANDOM_DATA_THEN_CLOSE)
      testSingleFileDownloadFaultWithoutCallback("test2.txt", 500, Fault.RANDOM_DATA_THEN_CLOSE)
      testSingleFileDownloadFaultWithoutCallback("test⫐1.jpeg", 200, Fault.MALFORMED_RESPONSE_CHUNK)
      testSingleFileDownloadFaultWithoutCallback("test2.txt", 500, Fault.MALFORMED_RESPONSE_CHUNK)
      testSingleFileDownloadFaultWithoutCallback("test⫐1.jpeg", 200, Fault.CONNECTION_RESET_BY_PEER)
      testSingleFileDownloadFaultWithoutCallback("test2.txt", 500, Fault.CONNECTION_RESET_BY_PEER)
      testSingleFileDownloadFaultWithoutCallback("test⫐1.jpeg", 200, Fault.EMPTY_RESPONSE)
      testSingleFileDownloadFaultWithoutCallback("test2.txt", 500, Fault.EMPTY_RESPONSE)

      "return 400 when empty payload" in {
        givenAuthorised()

        val result = wsClient
          .url(s"$url/transfer-multiple-files")
          .post(Json.obj())
          .futureValue

        result.status shouldBe 400
        verifyAuthorisationHasHappened()
        verifyFileUploadHaveNotHappen()
        verifyAuditRequestNotSent(FileTransmissionAuditEvent.MultipleFiles)
      }

      "return 400 when malformed payload" in {
        givenAuthorised()
        val conversationId = java.util.UUID.randomUUID().toString()

        val jsonBodyWritable =
          BodyWritable
            .apply[String](s => InMemoryBody(ByteString.fromString(s, StandardCharsets.UTF_8)), "application/json")

        val result = wsClient
          .url(s"$url/transfer-multiple-files")
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
        verifyFileUploadHaveNotHappen()
        verifyAuditRequestNotSent(FileTransmissionAuditEvent.MultipleFiles)
      }
    }
  }

  def testSingleFileTransferSuccessWithoutCallback(
    fileName: String,
    applicationName: String,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 201 when transfer of single $fileName for #$applicationName succeeds (no callback)" in new MultiFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val fileUrl =
        givenMultiFileTransferSucceeds(
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
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", applicationName, None)))
        .futureValue

      result.status shouldBe 201
      val resultBody = result.json.as[MultiFileTransferResult]
      resultBody.results.head should matchPattern {
        case FileTransferResult(_, true, 202, _, None) =>
      }
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHasHappened(1)
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testFileTransferBadRequest(description: String, fileTransferRequest: MultiFileTransferRequest) {
    s"return 400 when processing $description" in new MultiFileTransferTest(
      fileTransferRequest.files.head.fileName,
      Some(oneByteArray)
    ) {
      givenAuthorised()
      val fileUrl = "https://test.com/123"

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.toJson(fileTransferRequest))
        .futureValue

      result.status shouldBe 400
      verifyAuthorisationHasHappened()
      verifyFileDownloadHaveNotHappen(fileTransferRequest.files.head.fileName)
      verifyFileUploadHaveNotHappen()
      verifyAuditRequestNotSent(FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testSingleFileUploadFailureWithoutCallback(fileName: String, status: Int, bytesOpt: Option[Array[Byte]] = None) {
    s"return 201 when uploading $fileName fails because of $status (no callback)" in new MultiFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val fileUrl =
        givenMultiFileUploadFails(
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
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", "Route1", None)))
        .futureValue

      result.status shouldBe 201
      val resultBody = result.json.as[MultiFileTransferResult]
      resultBody.results.head should matchPattern {
        case FileTransferResult(_, false, `status`, _, Some(error)) if error == s"Error $status" =>
      }
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, if (status < 500) 1 else 3)
      verifyFileUploadHasHappened(if (status < 500) 1 else 3)
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testSingleFileDownloadFailureWithoutCallback(
    fileName: String,
    status: Int,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 201 when downloading $fileName fails because of $status (no callback)" in new MultiFileTransferTest(
      fileName,
      bytesOpt
    ) {
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
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", "Route1", None)))
        .futureValue

      result.status shouldBe 201
      val resultBody = result.json.as[MultiFileTransferResult]
      resultBody.results.head should matchPattern {
        case FileTransferResult(_, false, `status`, _, Some(error))
            if error == "This is an expected error requested by the test, no worries." =>
      }
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, if (status < 500) 1 else 3)
      verifyFileUploadHaveNotHappen()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testSingleFileDownloadFaultWithoutCallback(fileName: String, status: Int, fault: Fault) {
    s"return 201 when downloading $fileName fails because of $status with $fault (no callback)" in new MultiFileTransferTest(
      fileName
    ) {
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
        .url(s"$url/transfer-multiple-files")
        .post(Json.parse(jsonPayload("Risk-123", "Route1", None)))
        .futureValue

      result.status shouldBe 201
      val resultBody = result.json.as[MultiFileTransferResult]
      resultBody.results.head should matchPattern {
        case FileTransferResult(_, false, 0, _, Some(error)) =>
      }
      verifyAuthorisationHasHappened()
      verifyFileUploadHaveNotHappen()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

}
