package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.traderservices.support.WireMockSupport
import java.util.UUID
import com.github.tomakehurst.wiremock.http.Fault
import java.security.MessageDigest
import java.util.Base64
import java.nio.ByteBuffer
import scala.util.Try
import java.nio.charset.StandardCharsets
import java.{util => ju}
import uk.gov.hmrc.traderservices.models.FileTransferMetadataHeader
import java.net.URLEncoder
import java.io.InputStream
import java.io.ByteArrayInputStream

trait FileTransferStubs {
  me: WireMockSupport =>

  val FILE_TRANSFER_URL = "/cpr/filetransfer/caseevidence/v1"

  def givenFileTransferSucceeds(
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
      xmlMetadataHeader
    )

    downloadUrl
  }

  def givenFileTransferSucceeds(
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
      202,
      s"""{
         |"CaseReferenceNumber" : "$caseReferenceNumber",
         |"ApplicationType" : "$applicationName",
         |"OriginatingSystem" : "Digital",
         |"Content" : "$base64Content"
         |}""".stripMargin,
      checksum,
      xmlMetadataHeader
    )

    downloadUrl
  }

  def givenFileUploadFails(
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
      xmlMetadataHeader
    )

    downloadUrl
  }

  def givenFileDownloadFails(
    status: Int,
    caseReferenceNumber: String,
    applicationName: String,
    fileName: String,
    responseBody: String,
    base64Content: String,
    checksum: String,
    fileSize: Int,
    xmlMetadataHeader: String
  ): String = {
    val downloadUrl =
      stubForFileDownload(status, responseBody.getBytes(StandardCharsets.UTF_8), fileName)

    stubForFileUpload(
      202,
      s"""{
         |"CaseReferenceNumber" : "$caseReferenceNumber",
         |"ApplicationType" : "$applicationName",
         |"OriginatingSystem" : "Digital",
         |"Content" : "$base64Content"
         |}""".stripMargin,
      checksum,
      xmlMetadataHeader
    )

    downloadUrl
  }

  def givenFileDownloadFault(
    status: Int,
    fault: Fault,
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
      stubForFileDownload(status, fault)

    stubForFileUpload(
      202,
      s"""{
         |"CaseReferenceNumber" : "$caseReferenceNumber",
         |"ApplicationType" : "$applicationName",
         |"OriginatingSystem" : "Digital",
         |"Content" : "$base64Content"
         |}""".stripMargin,
      checksum,
      xmlMetadataHeader
    )

    downloadUrl
  }

  def verifyFileTransferHasHappened(times: Int = 1) =
    verify(times, postRequestedFor(urlEqualTo(FILE_TRANSFER_URL)))

  def verifyFileTransferDidNotHappen() =
    verify(0, postRequestedFor(urlEqualTo(FILE_TRANSFER_URL)))

  private def stubForFileUpload(status: Int, payload: String, checksum: String, xmlMetadataHeader: String): Unit =
    stubFor(
      post(urlEqualTo(FILE_TRANSFER_URL))
        .withHeader("x-correlation-id", matching("[A-Za-z0-9-]{36}"))
        .withHeader("x-conversation-id", matching("[A-Za-z0-9-]{36}"))
        .withHeader("customprocesseshost", equalTo("Digital"))
        .withHeader("date", matching("[A-Za-z0-9,: ]{29}"))
        .withHeader("accept", equalTo("application/json"))
        .withHeader("content-Type", equalTo("application/json"))
        .withHeader("authorization", equalTo("Bearer dummy-it-token"))
        .withHeader("checksumAlgorithm", equalTo("SHA-256"))
        .withHeader("checksum", equalTo(checksum))
        .withHeader(
          "x-metadata",
          if (xmlMetadataHeader.isEmpty) containing("xml")
          else equalToXml(xmlMetadataHeader, true, "\\{\\{", "\\}\\}")
        )
        .withRequestBody(equalToJson(payload, true, true))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
        )
    )

  private def stubForFileDownload(status: Int, bytes: Array[Byte], fileName: String): String = {

    val url = s"/bucket/${URLEncoder.encode(fileName, "UTF-8")}"

    stubFor(
      get(urlEqualTo(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/octet-stream")
            .withBody(bytes)
        )
    )

    url
  }

  private def stubForFileDownload(status: Int, fault: Fault): String = {
    val url = s"/bucket/${UUID.randomUUID().toString()}"

    stubFor(
      get(urlEqualTo(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/octet-stream")
            .withFault(fault)
        )
    )

    url
  }

  def givenTraderServicesFileTransferSucceeds(): Unit =
    stubFor(
      post(urlPathEqualTo("/transfer-file"))
        .willReturn(
          aResponse()
            .withStatus(202)
        )
    )

  def givenTraderServicesFileTransferFailure(status: Int): Unit =
    stubFor(
      post(urlPathEqualTo("/transfer-file"))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def verifyTraderServicesFileTransferHasHappened(times: Int = 1) =
    verify(times, postRequestedFor(urlPathEqualTo("/transfer-file")))

  abstract class FileTransferTest(fileName: String, bytesOpt: Option[Array[Byte]] = None) {
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
      fileSize = 543210,
      checksum = checksum,
      batchSize = 1,
      batchCount = 1
    ).toXmlString

    val fileUrl: String

    def jsonPayload(caseReferenceNumber: String, applicationName: String) =
      s"""{
         |"conversationId":"$conversationId",
         |"caseReferenceNumber":"$caseReferenceNumber",
         |"applicationName":"$applicationName",
         |"upscanReference":"XYZ0123456789",
         |"downloadUrl":"$wireMockBaseUrlAsString$fileUrl",
         |"fileName":"$fileName",
         |"fileMimeType":"image/jpeg",
         |"fileSize": 543210,
         |"checksum":"$checksum",
         |"batchSize": 1,
         |"batchCount": 1
         |}""".stripMargin
  }

  private val chunkSize: Int = 2400

  private val cache: collection.mutable.Map[String, (Array[Byte], String, String, Int)] =
    collection.mutable.Map()

  final def load(resource: String): (Array[Byte], String, String, Int) =
    cache
      .get(resource)
      .getOrElse(
        Try {
          val io = getClass.getResourceAsStream(resource)
          val result = read(io)
          cache.update(resource, result)
          result
        }
          .fold(e => throw new RuntimeException(s"Could not load $resource file", e), identity)
      )

  final def read(io: InputStream): (Array[Byte], String, String, Int) = {
    val digest = MessageDigest.getInstance("SHA-256")
    val chunk = Array.ofDim[Byte](chunkSize)
    val encoder = Base64.getEncoder()
    var hasNext = true
    val rawBuffer = ByteBuffer.allocate(10 * 1024 * 1024)
    val encodedBuffer = ByteBuffer.allocate(14 * 1024 * 1024)
    var fileSize = 0
    while (hasNext) {
      for (i <- 0 until chunkSize) chunk.update(i, 0)
      val readLength = io.read(chunk)
      hasNext = readLength != -1
      if (readLength >= 0) {
        fileSize = fileSize + readLength
        val chunkBytes =
          if (readLength == chunk.length) chunk
          else chunk.take(readLength)
        digest.update(chunkBytes)
        val encoded = encoder.encode(chunkBytes)
        encodedBuffer.put(encoded)
        rawBuffer.put(chunkBytes)
      }
    }
    val bytes = Array.ofDim[Byte](rawBuffer.position())
    rawBuffer.clear()
    rawBuffer.get(bytes)
    val encoded = Array.ofDim[Byte](encodedBuffer.position())
    encodedBuffer.clear()
    encodedBuffer.get(encoded)
    io.close()
    val checksum = digest.digest()
    val contentBase64 = new String(encoded, StandardCharsets.UTF_8)
    (bytes, contentBase64, convertBytesToHex(checksum), fileSize)
  }

  private def convertBytesToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes)
      sb.append(String.format("%02x", Byte.box(b)))
    sb.toString
  }

}
