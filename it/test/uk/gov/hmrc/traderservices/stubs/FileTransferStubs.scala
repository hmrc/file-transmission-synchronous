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

package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.http.HttpStatus
import uk.gov.hmrc.traderservices.models.FileTransferMetadataHeader
import uk.gov.hmrc.traderservices.models.FileTransferRequest
import uk.gov.hmrc.traderservices.support.WireMockSupport

import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.{util => ju}
import scala.util.Try

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
      xmlMetadataHeader,
      "Route1",
      caseReferenceNumber
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
      xmlMetadataHeader,
      applicationName,
      caseReferenceNumber
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
      xmlMetadataHeader,
      applicationName,
      caseReferenceNumber
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
      xmlMetadataHeader,
      applicationName,
      caseReferenceNumber
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
      stubForFileDownload(status, fileName, fault)

    stubForFileUpload(
      202,
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

  def verifyFileUploadHasHappened(times: Int = 1) =
    verify(times, postRequestedFor(urlEqualTo(FILE_TRANSFER_URL)))

  def verifyFileUploadHaveNotHappen() =
    verify(0, postRequestedFor(urlEqualTo(FILE_TRANSFER_URL)))

  def stubForFileUpload(
    status: Int,
    payload: String,
    checksum: String,
    xmlMetadataHeader: String,
    applicationName: String,
    caseReferenceNumber: String,
    delay: Int = 0
  ): Unit =
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
        .withHeader("referer", equalTo(applicationName))
        .withRequestBody(equalToJson(payload, true, true))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(if (HttpStatus.isSuccess(status)) "" else s"Error $status")
            .withFixedDelay(delay)
        )
    )

  def verifyFileDownloadHasHappened(fileName: String, times: Int) =
    verify(times, getRequestedFor(urlEqualTo(s"/bucket/${URLEncoder.encode(fileName, "UTF-8")}")))

  def verifyFileDownloadHasHappened(fileName: String, fault: Fault, times: Int) =
    verify(times, getRequestedFor(urlEqualTo(s"/bucket/${URLEncoder.encode(fileName, "UTF-8")}/${fault.name()}")))

  def verifyFileDownloadHaveNotHappen(fileName: String) =
    verify(0, getRequestedFor(urlEqualTo(s"/bucket/${URLEncoder.encode(fileName, "UTF-8")}")))

  def verifyFileDownloadHaveNotHappen() =
    verify(0, getRequestedFor(urlPathMatching("\\/bucket\\/.*")))

  def stubForFileDownload(status: Int, bytes: Array[Byte], fileName: String): String = {

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

  def stubForFileDownload(status: Int, fileName: String, fault: Fault): String = {
    val url = s"/bucket/${URLEncoder.encode(fileName, "UTF-8")}/${fault.name()}"

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
        MessageUtils.read(new ByteArrayInputStream(bytes))

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

    def jsonPayload(caseReferenceNumber: String, applicationName: String) =
      s"""{
         |"conversationId":"$conversationId",
         |"caseReferenceNumber":"$caseReferenceNumber",
         |"applicationName":"$applicationName",
         |"upscanReference":"XYZ0123456789",
         |"downloadUrl":"$wireMockBaseUrlAsString$fileUrl",
         |"fileName":"$fileName",
         |"fileMimeType":"image/jpeg",
         |"fileSize": ${bytes.length},
         |"checksum":"$checksum",
         |"batchSize": 1,
         |"batchCount": 1
         |}""".stripMargin

    def jsonDataPayload(caseReferenceNumber: String, applicationName: String) =
      s"""{
         |"conversationId":"$conversationId",
         |"caseReferenceNumber":"$caseReferenceNumber",
         |"applicationName":"$applicationName",
         |"upscanReference":"XYZ0123456789",
         |"downloadUrl":"data:image/jpeg;base64,$base64Content",
         |"fileName":"$fileName",
         |"fileMimeType":"image/jpeg",
         |"fileSize": ${bytes.length},
         |"checksum":"$checksum",
         |"batchSize": 1,
         |"batchCount": 1
         |}""".stripMargin
  }

  private val cache: collection.mutable.Map[String, (Array[Byte], String, String, Int)] =
    collection.mutable.Map()

  final def load(resource: String): (Array[Byte], String, String, Int) =
    cache
      .get(resource)
      .getOrElse(
        Try {
          val io = getClass.getResourceAsStream(resource)
          val result = MessageUtils.read(io)
          cache.update(resource, result)
          result
        }
          .fold(e => throw new RuntimeException(s"Could not load $resource file", e), identity)
      )

  val exampleRequest = FileTransferRequest(
    conversationId = "1090c5d7-d895-4f15-97b5-aa59ab7468b5",
    caseReferenceNumber = "PC12010081330XGBNZJO04",
    applicationName = "Route1",
    upscanReference = "XYZ0123456789",
    downloadUrl = "https://s3.amazon.aws/bucket/12345",
    checksum = "7dd6f04c468c1701cd5e43018fd32ab81c86ddddf90cd038651d4e405df715a4",
    fileName = "test.pdf",
    fileMimeType = "image/jpeg",
    fileSize = Some(12345),
    batchSize = 1,
    batchCount = 1
  )

}
