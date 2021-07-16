package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.traderservices.models.FileTransferData
import uk.gov.hmrc.traderservices.models.FileTransferMetadataHeader
import uk.gov.hmrc.traderservices.models.MultiFileTransferRequest
import uk.gov.hmrc.traderservices.support.WireMockSupport

import java.io.ByteArrayInputStream
import java.{util => ju}
import play.api.libs.json.Json
import uk.gov.hmrc.traderservices.models.MultiFileTransferResult
import uk.gov.hmrc.traderservices.models.FileTransferResult
import java.time.LocalDateTime
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import play.api.libs.json.JsString

trait MultiFileTransferStubs extends FileTransferStubs {
  me: WireMockSupport =>

  def givenMultiFileTransferSucceeds(
    caseReferenceNumber: String,
    fileName: String,
    conversationId: String
  ): String = {
    val (bytes, base64Content, checksum, fileSize) = load(s"/$fileName")
    val xmlMetadataHeader = FileTransferMetadataHeader(
      caseReferenceNumber = caseReferenceNumber,
      applicationName = "Route1",
      correlationId = "{{correlationId}}",
      conversationId = conversationId,
      sourceFileName = fileName,
      sourceFileMimeType = "image/jpeg",
      checksum = checksum,
      batchSize = 1,
      batchCount = 1
    ).toXmlString

    val downloadUrl =
      stubForFileDownload(200, bytes, fileName)

    stubForFileUpload(
      202,
      s"""{
         |"CaseReferenceNumber" : "$caseReferenceNumber",
         |"ApplicationType" : "Route1",
         |"OriginatingSystem" : "Digital",
         |"Content" : "$base64Content"
         |}""".stripMargin,
      checksum,
      xmlMetadataHeader,
      "Route1",
      caseReferenceNumber
    )

    downloadUrl
  }

  def givenMultiFileTransferSucceeds(
    caseReferenceNumber: String,
    applicationName: String,
    fileName: String,
    bytes: Array[Byte],
    base64Content: String,
    checksum: String,
    fileSize: Int,
    xmlMetadataHeader: String,
    callbackUrl: String,
    conversationId: String
  ): String = {
    val downloadUrl =
      givenMultiFileTransferSucceeds(
        caseReferenceNumber,
        applicationName,
        fileName,
        bytes,
        base64Content,
        checksum,
        fileSize,
        xmlMetadataHeader
      )

    val expectedResponse = MultiFileTransferResult(
      conversationId,
      "Risk-123",
      applicationName,
      Seq(
        FileTransferResult(
          "XYZ0123456789",
          checksum,
          fileName,
          "image/jpeg",
          fileSize,
          true,
          202,
          LocalDateTime.now,
          None
        )
      ),
      Some(Json.obj("foo" -> Json.obj("bar" -> 1), "zoo" -> JsString("zar")))
    )

    stubForCallback(callbackUrl, expectedCallbackPayload(expectedResponse))
    downloadUrl
  }

  def expectedCallbackPayload(expectedResponse: MultiFileTransferResult): String =
    Json.stringify {
      val payload = Json
        .toJson(expectedResponse)
        .as[JsObject]
      val results = payload("results").as[JsArray].value.map(x => x.as[JsObject].-("transferredAt"))
      payload.+(("results", JsArray(results)))
    }

  def stubForCallback(callbackUrl: String, callbackPayload: String) =
    stubFor(
      post(urlEqualTo(callbackUrl))
        .withRequestBody(equalToJson(callbackPayload, true, true))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

  def verifyCallbackHasHappened(callbackUrl: String, times: Int) =
    verify(
      times,
      postRequestedFor(urlEqualTo(callbackUrl))
    )

  def givenMultiFileTransferSucceeds(
    caseReferenceNumber: String,
    applicationName: String,
    fileName: String,
    bytes: Array[Byte],
    base64Content: String,
    checksum: String,
    fileSize: Int,
    xmlMetadataHeader: String,
    status: Int = 202
  ): String = {
    val downloadUrl =
      stubForFileDownload(200, bytes, fileName)

    stubForFileUpload(
      status,
      s"""{
         |"CaseReferenceNumber" : "$caseReferenceNumber",
         |"ApplicationType" : "$applicationName",
         |"OriginatingSystem" : "Digital",
         |"Content" : "$base64Content"
         |}""".stripMargin,
      checksum,
      xmlMetadataHeader,
      applicationName,
      caseReferenceNumber
    )

    downloadUrl
  }

  def givenMultiFileUploadFails(
    status: Int,
    caseReferenceNumber: String,
    applicationName: String,
    fileName: String,
    bytes: Array[Byte],
    base64Content: String,
    checksum: String,
    fileSize: Int,
    xmlMetadataHeader: String
  ): String = {
    val downloadUrl =
      stubForFileDownload(200, bytes, fileName)

    stubForFileUpload(
      status,
      s"""{
         |"CaseReferenceNumber" : "$caseReferenceNumber",
         |"ApplicationType" : "$applicationName",
         |"OriginatingSystem" : "Digital",
         |"Content" : "$base64Content"
         |}""".stripMargin,
      checksum,
      xmlMetadataHeader,
      applicationName,
      caseReferenceNumber
    )

    downloadUrl
  }

  def givenMultiFileUploadFails(
    status: Int,
    caseReferenceNumber: String,
    applicationName: String,
    fileName: String,
    bytes: Array[Byte],
    base64Content: String,
    checksum: String,
    fileSize: Int,
    xmlMetadataHeader: String,
    callbackUrl: String,
    conversationId: String
  ): String = {
    val downloadUrl =
      givenMultiFileUploadFails(
        status,
        caseReferenceNumber,
        applicationName,
        fileName,
        bytes,
        base64Content,
        checksum,
        fileSize,
        xmlMetadataHeader
      )

    givenCallbackForFailure(callbackUrl, conversationId, applicationName, fileName, checksum, fileSize, status)

    downloadUrl
  }

  def givenCallbackForFailure(
    callbackUrl: String,
    conversationId: String,
    applicationName: String,
    fileName: String,
    checksum: String,
    fileSize: Int,
    status: Int
  ) = {
    val expectedResponse = MultiFileTransferResult(
      conversationId,
      "Risk-123",
      applicationName,
      Seq(
        FileTransferResult(
          "XYZ0123456789",
          checksum,
          fileName,
          "image/jpeg",
          fileSize,
          false,
          status,
          LocalDateTime.now,
          None
        )
      )
    )

    stubForCallback(callbackUrl, expectedCallbackPayload(expectedResponse))
  }

  def givenTraderMultiServicesFileTransferSucceeds(): Unit =
    stubFor(
      post(urlPathEqualTo("/transfer-multiple-files"))
        .willReturn(
          aResponse()
            .withStatus(202)
        )
    )

  def givenTraderServicesMultiFileTransferFailure(status: Int): Unit =
    stubFor(
      post(urlPathEqualTo("/transfer-multiple-files"))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def verifyTraderServicesMultiFileTransferHasHappened(times: Int = 1) =
    verify(times, postRequestedFor(urlPathEqualTo("/transfer-multiple-files")))

  abstract class SingleFileTransferTest(fileName: String, bytesOpt: Option[Array[Byte]] = None) {
    val correlationId = ju.UUID.randomUUID().toString()
    val conversationId = ju.UUID.randomUUID().toString()
    val (bytes, base64Content, checksum, fileSize) = bytesOpt match {
      case Some(bytes) =>
        read(new ByteArrayInputStream(bytes))

      case None =>
        load(s"/$fileName")
    }
    val xmlMetadataHeader = FileTransferMetadataHeader(
      caseReferenceNumber = "Risk-123",
      applicationName = "Route1",
      correlationId = correlationId,
      conversationId = conversationId,
      sourceFileName = fileName,
      sourceFileMimeType = "image/jpeg",
      fileSize = bytes.length,
      checksum = checksum,
      batchSize = 1,
      batchCount = 1
    ).toXmlString

    val fileUrl: String

    def jsonPayload(caseReferenceNumber: String, applicationName: String, callbackUrlOpt: Option[String]) =
      s"""{
         |"conversationId":"$conversationId",
         |"caseReferenceNumber":"$caseReferenceNumber",
         |"applicationName":"$applicationName",
         |"files":[{
         |  "upscanReference":"XYZ0123456789",
         |  "downloadUrl":"$wireMockBaseUrlAsString$fileUrl",
         |  "fileName":"$fileName",
         |  "fileMimeType":"image/jpeg",
         |  "fileSize": ${bytes.length},
         |  "checksum":"$checksum"
         |}],
         |"metadata":{"foo":{"bar":1},"zoo":"zar"}
         |${callbackUrlOpt
        .map(callbackUrl => s"""
         |,"callbackUrl":"$wireMockBaseUrlAsString$callbackUrl"""".stripMargin)
        .getOrElse("")}}""".stripMargin
  }

  case class TestFileTransfer(
    fileName: String,
    upscanReference: String,
    bytes: Array[Byte],
    base64Content: String,
    checksum: String,
    fileSize: Int,
    xmlMetadataHeader: String,
    correlationId: String,
    status: Int,
    fileMimeType: String
  )

  abstract class MultiFileTransferTest(files: Seq[(String, Option[Array[Byte]], Int)]) {

    def fileUrl(f: TestFileTransfer): String

    val conversationId = ju.UUID.randomUUID().toString()

    val testFileTransfers: Seq[TestFileTransfer] = files.map {
      case (fileName, bytesOpt, status) =>
        val (bytes, base64Content, checksum, fileSize) = bytesOpt match {
          case Some(bytes) =>
            read(new ByteArrayInputStream(bytes))

          case None =>
            load(s"/$fileName")
        }

        val upscanReference = fileName.reverse
        val correlationId = ju.UUID.randomUUID().toString()

        val xmlMetadataHeader = FileTransferMetadataHeader(
          caseReferenceNumber = "Risk-123",
          applicationName = "Route1",
          correlationId = correlationId,
          conversationId = conversationId,
          sourceFileName = fileName,
          sourceFileMimeType = "image/jpeg",
          fileSize = fileSize,
          checksum = checksum,
          batchSize = 1,
          batchCount = 1
        ).toXmlString

        TestFileTransfer(
          fileName,
          upscanReference,
          bytes,
          base64Content,
          checksum,
          fileSize,
          xmlMetadataHeader,
          correlationId,
          status,
          "image/jpeg"
        )
    }

    def jsonPayload(caseReferenceNumber: String, applicationName: String, callbackUrlOpt: Option[String]) =
      s"""{
         |"conversationId":"$conversationId",
         |"caseReferenceNumber":"$caseReferenceNumber",
         |"applicationName":"$applicationName",
         |"files":[${testFileTransfers
        .map(f => s"""{
         |  "upscanReference":"${f.upscanReference}",
         |  "downloadUrl":"$wireMockBaseUrlAsString${fileUrl(f)}",
         |  "fileName":"${f.fileName}",
         |  "fileMimeType":"image/jpeg",
         |  "fileSize": ${f.fileSize},
         |  "checksum":"${f.checksum}"
         |}""")
        .mkString(",")}],
         |"metadata":{"foo":{"bar":1},"zoo":"zar"}
         |${callbackUrlOpt
        .map(callbackUrl => s"""
         |,"callbackUrl":"$wireMockBaseUrlAsString$callbackUrl"""".stripMargin)
        .getOrElse("")}}""".stripMargin
  }

  val exampleSingleFileRequest = MultiFileTransferRequest(
    conversationId = "1090c5d7-d895-4f15-97b5-aa59ab7468b5",
    caseReferenceNumber = "PC12010081330XGBNZJO04",
    applicationName = "Route1",
    files = Seq(
      FileTransferData(
        upscanReference = "XYZ0123456789",
        downloadUrl = "https://s3.amazon.aws/bucket/12345",
        checksum = "7dd6f04c468c1701cd5e43018fd32ab81c86ddddf90cd038651d4e405df715a4",
        fileName = "test.pdf",
        fileMimeType = "image/jpeg",
        fileSize = Some(12345)
      )
    )
  )

}
