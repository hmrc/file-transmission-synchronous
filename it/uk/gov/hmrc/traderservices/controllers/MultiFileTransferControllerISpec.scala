package uk.gov.hmrc.traderservices.controllers

import akka.util.ByteString
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.Suite
import org.scalatestplus.play.ServerProvider
import play.api.libs.json.Json
import play.api.libs.ws.BodyWritable
import play.api.libs.ws.InMemoryBody
import play.api.libs.ws.WSClient
import uk.gov.hmrc.traderservices.models.FileTransferResult
import uk.gov.hmrc.traderservices.models.MultiFileTransferRequest
import uk.gov.hmrc.traderservices.models.MultiFileTransferResult
import uk.gov.hmrc.traderservices.services.FileTransmissionAuditEvent
import uk.gov.hmrc.traderservices.stubs._
import uk.gov.hmrc.traderservices.support.JsonMatchers
import uk.gov.hmrc.traderservices.support.ServerBaseISpec

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.UUID
import play.api.libs.json.JsString

class MultiFileTransferControllerISpec
    extends ServerBaseISpec with AuthStubs with MultiFileTransferStubs with JsonMatchers {
  this: Suite with ServerProvider =>

  val url = s"http://localhost:$port"

  val dateTime = LocalDateTime.now()

  val wsClient = app.injector.instanceOf[WSClient]

  val oneByteArray = Array.fill[Byte](1)(255.toByte)
  val twoBytesArray = Array.fill[Byte](2)(255.toByte)
  val threeBytesArray = Array.fill[Byte](3)(255.toByte)

  object positiveInteger {
    def unapply(i: Int): Option[Int] =
      if (i > 0) Some(i) else None
  }

  "MultiFileTransferController" when {

    "POST /transfer-multiple-files" should {
      testFileTransferBadRequest(
        "request with an empty conversationId",
        exampleSingleFileRequest.copy(conversationId = "")
      )
      testFileTransferBadRequest(
        "request with an empty applicationName",
        exampleSingleFileRequest.copy(applicationName = "")
      )
      testFileTransferBadRequest(
        "request with invalid applicationName",
        exampleSingleFileRequest.copy(applicationName = "FOO")
      )

      for (applicationName <- Seq("Route1", "NDRC", "C18", "FAS")) {
        testSingleFileTransferSuccessWithoutCallback("oneByteArray", applicationName, Some(oneByteArray))
        testSingleFileTransferSuccessWithoutCallback("twoBytesArray", applicationName, Some(twoBytesArray))
        testSingleFileTransferSuccessWithoutCallback("threeBytesArray", applicationName, Some(threeBytesArray))
        testSingleFileTransferSuccessWithoutCallback("prod.routes", applicationName)
        testSingleFileTransferSuccessWithoutCallback("app.routes", applicationName)
        testSingleFileTransferSuccessWithoutCallback("schema.json", applicationName)
        testSingleFileTransferSuccessWithoutCallback("logback.xml", applicationName)
        testSingleFileTransferSuccessWithoutCallback("test⫐1.jpeg", applicationName)
        testSingleFileTransferSuccessWithoutCallback("test2.txt", applicationName)

        testSingleDataTransferSuccessWithoutCallback("oneByteArray", applicationName, Some(oneByteArray))
        testSingleDataTransferSuccessWithoutCallback("twoBytesArray", applicationName, Some(twoBytesArray))
        testSingleDataTransferSuccessWithoutCallback("threeBytesArray", applicationName, Some(threeBytesArray))
        testSingleDataTransferSuccessWithoutCallback("prod.routes", applicationName)
        testSingleDataTransferSuccessWithoutCallback("app.routes", applicationName)
        testSingleDataTransferSuccessWithoutCallback("schema.json", applicationName)
        testSingleDataTransferSuccessWithoutCallback("logback.xml", applicationName)
        testSingleDataTransferSuccessWithoutCallback("test⫐1.jpeg", applicationName)
        testSingleDataTransferSuccessWithoutCallback("test2.txt", applicationName)

        testSingleFileTransferSuccessWithCallback("oneByteArray", applicationName, Some(oneByteArray))
        testSingleFileTransferSuccessWithCallback("twoBytesArray", applicationName, Some(twoBytesArray))
        testSingleFileTransferSuccessWithCallback("threeBytesArray", applicationName, Some(threeBytesArray))
        testSingleFileTransferSuccessWithCallback("prod.routes", applicationName)
        testSingleFileTransferSuccessWithCallback("app.routes", applicationName)
        testSingleFileTransferSuccessWithCallback("schema.json", applicationName)
        testSingleFileTransferSuccessWithCallback("logback.xml", applicationName)
        testSingleFileTransferSuccessWithCallback("test⫐1.jpeg", applicationName)
        testSingleFileTransferSuccessWithCallback("test2.txt", applicationName)

        testMultipleFilesTransferWithoutCallback(
          applicationName,
          Seq(
            ("oneByteArray", Some(oneByteArray), 201),
            ("twoBytesArray", Some(twoBytesArray), 200),
            ("threeBytesArray", Some(threeBytesArray), 203),
            ("prod.routes", None, 202),
            ("app.routes", None, 202),
            ("schema.json", None, 202),
            ("logback.xml", None, 202),
            ("test⫐1.jpeg", None, 202),
            ("test2.txt", None, 202)
          )
        )
        testMultipleFilesTransferWithoutCallback(
          applicationName,
          Seq(
            ("oneByteArray", Some(oneByteArray), 404),
            ("twoBytesArray", Some(twoBytesArray), 500),
            ("threeBytesArray", Some(threeBytesArray), 202),
            ("prod.routes", None, 200),
            ("app.routes", None, 400),
            ("schema.json", None, 403),
            ("logback.xml", None, 500),
            ("test⫐1.jpeg", None, 599),
            ("test2.txt", None, 202)
          )
        )

        testMultipleFilesTransferWithCallback(
          applicationName,
          Seq(
            ("oneByteArray", Some(oneByteArray), 201),
            ("twoBytesArray", Some(twoBytesArray), 200),
            ("threeBytesArray", Some(threeBytesArray), 203),
            ("prod.routes", None, 202),
            ("app.routes", None, 202),
            ("schema.json", None, 202),
            ("logback.xml", None, 202),
            ("test⫐1.jpeg", None, 202),
            ("test2.txt", None, 202)
          )
        )
        testMultipleFilesTransferWithCallback(
          applicationName,
          Seq(
            ("oneByteArray", Some(oneByteArray), 404),
            ("twoBytesArray", Some(twoBytesArray), 500),
            ("threeBytesArray", Some(threeBytesArray), 202),
            ("prod.routes", None, 200),
            ("app.routes", None, 400),
            ("schema.json", None, 403),
            ("logback.xml", None, 500),
            ("test⫐1.jpeg", None, 599),
            ("test2.txt", None, 202)
          )
        )
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 400)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 403)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 404)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 429)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 499)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 500)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 501)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 502)
        testCallbackFailure("oneByteArray", applicationName, Some(oneByteArray), 503)

        testCallbackFault("oneByteArray", applicationName, Some(oneByteArray), Fault.CONNECTION_RESET_BY_PEER)
        testCallbackFault("oneByteArray", applicationName, Some(oneByteArray), Fault.EMPTY_RESPONSE)
        testCallbackFault("oneByteArray", applicationName, Some(oneByteArray), Fault.MALFORMED_RESPONSE_CHUNK)
        testCallbackFault("oneByteArray", applicationName, Some(oneByteArray), Fault.RANDOM_DATA_THEN_CLOSE)
      }

      testSingleFileUploadFailureWithoutCallback("oneByteArray", 404, Some(oneByteArray))
      testSingleFileUploadFailureWithoutCallback("twoBytesArray", 404, Some(twoBytesArray))
      testSingleFileUploadFailureWithoutCallback("threeBytesArray", 404, Some(threeBytesArray))
      testSingleFileUploadFailureWithoutCallback("prod.routes", 500)
      testSingleFileUploadFailureWithoutCallback("app.routes", 404)
      testSingleFileUploadFailureWithoutCallback("schema.json", 501)
      testSingleFileUploadFailureWithoutCallback("logback.xml", 409)
      testSingleFileUploadFailureWithoutCallback("test⫐1.jpeg", 403)
      testSingleFileUploadFailureWithoutCallback("test⫐1.jpeg", 499)
      testSingleFileUploadFailureWithoutCallback("test⫐1.jpeg", 429)

      testSingleFileUploadFailureWithCallback("oneByteArray", 404, Some(oneByteArray))
      testSingleFileUploadFailureWithCallback("twoBytesArray", 404, Some(twoBytesArray))
      testSingleFileUploadFailureWithCallback("threeBytesArray", 404, Some(threeBytesArray))
      testSingleFileUploadFailureWithCallback("prod.routes", 500)
      testSingleFileUploadFailureWithCallback("app.routes", 404)
      testSingleFileUploadFailureWithCallback("schema.json", 501)
      testSingleFileUploadFailureWithCallback("logback.xml", 409)
      testSingleFileUploadFailureWithCallback("test⫐1.jpeg", 403)
      testSingleFileUploadFailureWithCallback("test⫐1.jpeg", 429)
      testSingleFileUploadFailureWithCallback("test⫐1.jpeg", 499)

      testSingleFileDownloadFailureWithoutCallback("oneByteArray", 404, Some(oneByteArray))
      testSingleFileDownloadFailureWithoutCallback("twoBytesArray", 404, Some(twoBytesArray))
      testSingleFileDownloadFailureWithoutCallback("threeBytesArray", 404, Some(threeBytesArray))
      testSingleFileDownloadFailureWithoutCallback("prod.routes", 400)
      testSingleFileDownloadFailureWithoutCallback("app.routes", 403)
      testSingleFileDownloadFailureWithoutCallback("schema.json", 500)
      testSingleFileDownloadFailureWithoutCallback("logback.xml", 501)
      testSingleFileDownloadFailureWithoutCallback("test⫐1.jpeg", 404)

      testSingleFileDownloadFailureWithCallback("oneByteArray", 404, Some(oneByteArray))
      testSingleFileDownloadFailureWithCallback("twoBytesArray", 404, Some(twoBytesArray))
      testSingleFileDownloadFailureWithCallback("threeBytesArray", 404, Some(threeBytesArray))
      testSingleFileDownloadFailureWithCallback("prod.routes", 400)
      testSingleFileDownloadFailureWithCallback("app.routes", 403)
      testSingleFileDownloadFailureWithCallback("schema.json", 500)
      testSingleFileDownloadFailureWithCallback("logback.xml", 501)
      testSingleFileDownloadFailureWithCallback("test⫐1.jpeg", 404)

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

      "return 500 when authorisation fails" in new SingleFileTransferTest(
        "foo.jpeg",
        Some(oneByteArray)
      ) {
        givenAuthorisationFails(403)
        val fileUrl =
          givenMultiFileTransferSucceeds(
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
          .url(s"$url/transfer-multiple-files")
          .withHttpHeaders("x-correlation-id" -> correlationId)
          .post(Json.parse(jsonPayload("Risk-123", "Route1", None)))
          .futureValue

        result.status shouldBe 500
        result.json should haveProperty[String]("errorCode", be("ERROR_UNKNOWN"))
        verifyAuthorisationHasHappened()
        verifyFileDownloadHaveNotHappen()
        verifyFileUploadHaveNotHappen()
      }
    }
  }

  def testSingleFileTransferSuccessWithoutCallback(
    fileName: String,
    applicationName: String,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 201 when transfer of a single file $fileName for #$applicationName succeeds (no callback)" in new SingleFileTransferTest(
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
        case FileTransferResult(
              _,
              `checksum`,
              `fileName`,
              "image/jpeg",
              `fileSize`,
              true,
              202,
              _,
              _,
              positiveInteger(duration),
              None
            ) =>
      }
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHasHappened(1)
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testSingleDataTransferSuccessWithoutCallback(
    fileName: String,
    applicationName: String,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 201 when transfer of a single data $fileName for #$applicationName succeeds (no callback)" in new SingleFileTransferTest(
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
        .post(Json.parse(jsonDataPayload("Risk-123", applicationName, None)))
        .futureValue

      result.status shouldBe 201
      val resultBody = result.json.as[MultiFileTransferResult]
      resultBody.results.head should matchPattern {
        case FileTransferResult(
              _,
              `checksum`,
              `fileName`,
              "image/jpeg",
              `fileSize`,
              true,
              202,
              _,
              _,
              positiveInteger(duration),
              None
            ) =>
      }
      verifyAuthorisationHasHappened()
      verifyFileDownloadHaveNotHappen()
      verifyFileUploadHasHappened(1)
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testSingleFileTransferSuccessWithCallback(
    fileName: String,
    applicationName: String,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 202 when transfer of a single file $fileName for #$applicationName succeeds (with callback)" in new SingleFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val callbackUrl = s"/foo/${UUID.randomUUID()}"
      val fileUrl =
        givenMultiFileTransferSucceeds(
          "Risk-123",
          applicationName,
          fileName,
          bytes,
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader,
          callbackUrl,
          conversationId
        )

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", applicationName, Some(callbackUrl))))
        .futureValue

      result.status shouldBe 202

      verifyAuthorisationHasHappened()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHasHappened(1)
      verifyCallbackHasHappened(callbackUrl, 1)
    }
  }

  def testSingleDataTransferSuccessWithCallback(
    fileName: String,
    applicationName: String,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 202 when transfer of a single data $fileName for #$applicationName succeeds (with callback)" in new SingleFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val callbackUrl = s"/foo/${UUID.randomUUID()}"
      val fileUrl =
        givenMultiFileTransferSucceeds(
          "Risk-123",
          applicationName,
          fileName,
          bytes,
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader,
          callbackUrl,
          conversationId
        )

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonDataPayload("Risk-123", applicationName, Some(callbackUrl))))
        .futureValue

      result.status shouldBe 202

      verifyAuthorisationHasHappened()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
      verifyFileDownloadHaveNotHappen()
      verifyFileUploadHasHappened(1)
      verifyCallbackHasHappened(callbackUrl, 1)
    }
  }

  def testMultipleFilesTransferWithoutCallback(
    applicationName: String,
    files: Seq[(String, Option[Array[Byte]], Int)]
  ) {
    s"return 201 when transfering multiple files: ${files.map(f => s"${f._1} as ${f._3}").mkString(", ")} for #$applicationName (no callback)" in new MultiFileTransferTest(
      files
    ) {
      givenAuthorised()
      override def fileUrl(f: TestFileTransfer): String =
        if (f.status < 300)
          givenMultiFileTransferSucceeds(
            "Risk-123",
            applicationName,
            f.fileName,
            f.bytes,
            f.base64Content,
            f.checksum,
            f.fileSize,
            f.xmlMetadataHeader,
            f.status
          )
        else
          givenMultiFileUploadFails(
            f.status,
            "Risk-123",
            applicationName,
            f.fileName,
            f.bytes,
            f.base64Content,
            f.checksum,
            f.fileSize,
            f.xmlMetadataHeader
          )

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> UUID.randomUUID().toString())
        .post(Json.parse(jsonPayload("Risk-123", applicationName, None)))
        .futureValue

      result.status shouldBe 201
      val resultBody = result.json.as[MultiFileTransferResult]
      resultBody.results.foreach { r =>
        val f = testFileTransfers.find(_.upscanReference == r.upscanReference).get
        if (f.status < 300)
          r should matchPattern {
            case FileTransferResult(
                  r.upscanReference,
                  _,
                  _,
                  _,
                  _,
                  true,
                  f.status,
                  _,
                  _,
                  positiveInteger(duration),
                  None
                ) =>
          }
        else
          r should matchPattern {
            case FileTransferResult(
                  r.upscanReference,
                  _,
                  _,
                  _,
                  _,
                  false,
                  f.status,
                  _,
                  _,
                  positiveInteger(duration),
                  Some(_)
                ) =>
          }
      }
      verifyAuthorisationHasHappened()
      testFileTransfers
        .foreach(f => verifyFileDownloadHasHappened(f.fileName, if (Retry.shouldRetry(f.status)) 3 else 1))
      val expectedNumberOfUploads =
        testFileTransfers.map(f => if (Retry.shouldRetry(f.status)) 3 else 1).sum
      verifyFileUploadHasHappened(expectedNumberOfUploads)
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testMultipleFilesTransferWithCallback(
    applicationName: String,
    files: Seq[(String, Option[Array[Byte]], Int)]
  ) {
    s"return 202 when transfering multiple files: ${files.map(f => s"${f._1} as ${f._3}").mkString(", ")} for #$applicationName (with callback)" in new MultiFileTransferTest(
      files
    ) {
      givenAuthorised()
      val callbackUrl = s"/foo/${UUID.randomUUID()}"

      override def fileUrl(f: TestFileTransfer): String =
        if (f.status < 300)
          givenMultiFileTransferSucceeds(
            "Risk-123",
            applicationName,
            f.fileName,
            f.bytes,
            f.base64Content,
            f.checksum,
            f.fileSize,
            f.xmlMetadataHeader,
            f.status
          )
        else
          givenMultiFileUploadFails(
            f.status,
            "Risk-123",
            applicationName,
            f.fileName,
            f.bytes,
            f.base64Content,
            f.checksum,
            f.fileSize,
            f.xmlMetadataHeader
          )

      val expectedResponse =
        MultiFileTransferResult(
          conversationId,
          "Risk-123",
          applicationName,
          testFileTransfers
            .map(f =>
              FileTransferResult(
                f.upscanReference,
                f.checksum,
                f.fileName,
                f.fileMimeType,
                f.fileSize,
                f.status < 300,
                f.status,
                LocalDateTime.now,
                "",
                123,
                None
              )
            ),
          0,
          Some(Json.obj("foo" -> Json.obj("bar" -> 1), "zoo" -> JsString("zar")))
        )

      stubForCallback(callbackUrl, expectedCallbackPayload(expectedResponse), 200)

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> UUID.randomUUID().toString())
        .post(Json.parse(jsonPayload("Risk-123", applicationName, Some(callbackUrl))))
        .futureValue

      result.status shouldBe 202
      verifyAuthorisationHasHappened()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
      testFileTransfers
        .foreach(f => verifyFileDownloadHasHappened(f.fileName, if (Retry.shouldRetry(f.status)) 3 else 1))
      val expectedNumberOfUploads =
        testFileTransfers.map(f => if (Retry.shouldRetry(f.status)) 3 else 1).sum
      verifyFileUploadHasHappened(expectedNumberOfUploads)
      verifyCallbackHasHappened(callbackUrl, 1)
    }
  }

  def testFileTransferBadRequest(description: String, fileTransferRequest: MultiFileTransferRequest) {
    s"return 400 when processing $description" in new SingleFileTransferTest(
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
    s"return 201 when uploading $fileName fails because of $status (no callback)" in new SingleFileTransferTest(
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
        case FileTransferResult(
              _,
              `checksum`,
              `fileName`,
              "image/jpeg",
              `fileSize`,
              false,
              `status`,
              _,
              _,
              _,
              Some(error)
            ) if error == s"Error $status" =>
      }
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, if (Retry.shouldRetry(status)) 3 else 1)
      verifyFileUploadHasHappened(if (Retry.shouldRetry(status)) 3 else 1)
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testSingleFileUploadFailureWithCallback(fileName: String, status: Int, bytesOpt: Option[Array[Byte]] = None) {
    s"return 202 when uploading $fileName fails because of $status (with callback)" in new SingleFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val callbackUrl = s"/foo/${UUID.randomUUID()}"
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
          xmlMetadataHeader,
          callbackUrl,
          conversationId
        )

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", "Route1", Some(callbackUrl))))
        .futureValue

      result.status shouldBe 202
      verifyAuthorisationHasHappened()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
      verifyFileDownloadHasHappened(fileName, if (Retry.shouldRetry(status)) 3 else 1)
      verifyFileUploadHasHappened(if (Retry.shouldRetry(status)) 3 else 1)
      verifyCallbackHasHappened(callbackUrl, 1)
    }
  }

  def testSingleFileDownloadFailureWithoutCallback(
    fileName: String,
    status: Int,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 201 when downloading $fileName fails because of $status (no callback)" in new SingleFileTransferTest(
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
        case FileTransferResult(
              _,
              `checksum`,
              `fileName`,
              "image/jpeg",
              `fileSize`,
              false,
              `status`,
              _,
              _,
              _,
              Some(error)
            ) if error == "This is an expected error requested by the test, no worries." =>
      }
      verifyAuthorisationHasHappened()
      verifyFileDownloadHasHappened(fileName, if (Retry.shouldRetry(status)) 3 else 1)
      verifyFileUploadHaveNotHappen()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testSingleFileDownloadFailureWithCallback(
    fileName: String,
    status: Int,
    bytesOpt: Option[Array[Byte]] = None
  ) {
    s"return 202 when downloading $fileName fails because of $status (with callback)" in new SingleFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val callbackUrl = s"/foo/${UUID.randomUUID()}"
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

      givenCallbackForFailure(callbackUrl, conversationId, "Route1", fileName, checksum, fileSize, status)

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", "Route1", Some(callbackUrl))))
        .futureValue

      result.status shouldBe 202

      verifyAuthorisationHasHappened()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
      verifyFileDownloadHasHappened(fileName, if (Retry.shouldRetry(status)) 3 else 1)
      verifyFileUploadHaveNotHappen()
      verifyCallbackHasHappened(callbackUrl, 1)
    }
  }

  def testSingleFileDownloadFaultWithoutCallback(fileName: String, status: Int, fault: Fault) {
    s"return 201 when downloading $fileName fails because of $status with $fault (no callback)" in new SingleFileTransferTest(
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
        case FileTransferResult(_, `checksum`, `fileName`, "image/jpeg", `fileSize`, false, 0, _, _, _, Some(error)) =>
      }
      verifyAuthorisationHasHappened()
      verifyFileUploadHaveNotHappen()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
    }
  }

  def testCallbackFailure(
    fileName: String,
    applicationName: String,
    bytesOpt: Option[Array[Byte]] = None,
    callbackStatus: Int
  ) {
    s"return 202 when transfer of a single file $fileName for #$applicationName succeeds but callback fails with $callbackStatus" in new SingleFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val callbackUrl = s"/foo/${UUID.randomUUID()}"
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

      stubForCallback(callbackUrl, callbackStatus)

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", applicationName, Some(callbackUrl))))
        .futureValue

      result.status shouldBe 202

      verifyAuthorisationHasHappened()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHasHappened(1)
      verifyCallbackHasHappened(callbackUrl, if (Retry.shouldRetry(callbackStatus)) 3 else 1)
    }
  }

  def testCallbackFault(
    fileName: String,
    applicationName: String,
    bytesOpt: Option[Array[Byte]] = None,
    callbackFault: Fault
  ) {
    s"return 202 when transfer of a single file $fileName for #$applicationName succeeds but callback fails because of $callbackFault" in new SingleFileTransferTest(
      fileName,
      bytesOpt
    ) {
      givenAuthorised()
      val callbackUrl = s"/foo/${UUID.randomUUID()}"
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

      stubForCallback(callbackUrl, callbackFault)

      val result = wsClient
        .url(s"$url/transfer-multiple-files")
        .withHttpHeaders("x-correlation-id" -> correlationId)
        .post(Json.parse(jsonPayload("Risk-123", applicationName, Some(callbackUrl))))
        .futureValue

      result.status shouldBe 202

      verifyAuthorisationHasHappened()
      verifyAuditRequestSent(1, FileTransmissionAuditEvent.MultipleFiles)
      verifyFileDownloadHasHappened(fileName, 1)
      verifyFileUploadHasHappened(1)
      verifyCallbackHasHappened(callbackUrl, 1)
    }
  }

}
