/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.Date
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.Logger
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.utilities.FileNameUtils
import uk.gov.hmrc.traderservices.wiring.AppConfig

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.net.URLStreamHandler
import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/** A Flow modelling transfer of a single file (download and upload).
  */
trait FileTransferFlow {

  val appConfig: AppConfig
  val unitInterval: FiniteDuration

  implicit val materializer: Materializer
  implicit val actorSystem: ActorSystem

  final val MAX_FILENAME_LENGTH = 255
  final val DEFAULT_FILE_SIZE_IF_MISSING = 1024
  final val MAX_ERROR_HTTP_BODY_LENGTH = 10240

  private val connectionPool: Flow[
    (HttpRequest, (FileTransferRequest, HttpRequest)),
    (Try[HttpResponse], (FileTransferRequest, HttpRequest)),
    NotUsed
  ] = Http()
    .superPool[(FileTransferRequest, HttpRequest)]()

  final def createFileUploadHttpRequest(
    fileTransferRequest: FileTransferRequest,
    dataBytes: Source[ByteString, Any]
  ): HttpRequest = {
    val jsonHeader = s"""{
                        |    "CaseReferenceNumber":"${fileTransferRequest.caseReferenceNumber}",
                        |    "ApplicationType":"${fileTransferRequest.applicationName}",
                        |    "OriginatingSystem":"Digital",
                        |    "Content":"""".stripMargin

    val jsonFooter = "\"\n}"

    val fileEncodeAndWrapSource: Source[ByteString, Future[FileSizeAndChecksum]] =
      dataBytes
        .viaMat(EncodeFileBase64)(Keep.right)
        .prepend(Source.single(ByteString(jsonHeader, StandardCharsets.UTF_8)))
        .concat(Source.single(ByteString(jsonFooter, StandardCharsets.UTF_8)))

    val correlationId = fileTransferRequest.correlationId.getOrElse("")

    val sourceFileName =
      FileNameUtils.sanitize(MAX_FILENAME_LENGTH)(fileTransferRequest.fileName, correlationId)

    val xmlMetadata = FileTransferMetadataHeader(
      caseReferenceNumber = fileTransferRequest.caseReferenceNumber,
      applicationName = fileTransferRequest.applicationName,
      correlationId = correlationId,
      conversationId = fileTransferRequest.conversationId,
      sourceFileName = sourceFileName,
      sourceFileMimeType = fileTransferRequest.fileMimeType,
      fileSize = fileTransferRequest.fileSize.getOrElse(DEFAULT_FILE_SIZE_IF_MISSING),
      checksum = fileTransferRequest.checksum,
      batchSize = fileTransferRequest.batchSize,
      batchCount = fileTransferRequest.batchCount
    ).toXmlString

    HttpRequest(
      method = HttpMethods.POST,
      uri = appConfig.eisBaseUrl + appConfig.eisFileTransferApiPath,
      headers = collection.immutable.Seq(
        RawHeader("x-request-id", fileTransferRequest.requestId.getOrElse("-")),
        RawHeader("x-conversation-id", fileTransferRequest.conversationId),
        RawHeader(
          "x-correlation-id",
          fileTransferRequest.correlationId.getOrElse(
            throw new IllegalArgumentException("Missing correlationId argument of FileTransferRequest")
          )
        ),
        RawHeader("customprocesseshost", "Digital"),
        RawHeader("accept", "application/json"),
        RawHeader("authorization", s"Bearer ${appConfig.eisAuthorizationToken}"),
        RawHeader("checksumAlgorithm", "SHA-256"),
        RawHeader("checksum", fileTransferRequest.checksum),
        Date(DateTime.now),
        RawHeader("x-metadata", xmlMetadata),
        RawHeader("referer", fileTransferRequest.applicationName)
      ),
      entity = HttpEntity.apply(ContentTypes.`application/json`, fileEncodeAndWrapSource)
    )
  }

  /** Akka Stream flow:
    *   - requests downloading the file,
    *   - encodes file content stream using base64,
    *   - wraps base64 content in a json payload,
    *   - forwards to the upstream endpoint.
    */
  final val fileTransferFlow: Flow[
    FileTransferRequest,
    (Try[HttpResponse], (FileTransferRequest, HttpRequest)),
    NotUsed
  ] =
    Flow[FileTransferRequest]
      .map { fileTransferRequest =>
        Logger(getClass).info(
          s"Starting transfer requested by ${fileTransferRequest.applicationName} with conversationId=${fileTransferRequest.conversationId} [correlationId=${fileTransferRequest.correlationId
              .getOrElse("")}] of the file ${fileTransferRequest.upscanReference}, expected SHA-256 checksum is ${fileTransferRequest.checksum}, request startTime is ${fileTransferRequest.startTime} ..."
        )
        val fileDownloadHttpRequest = HttpRequest(
          method = HttpMethods.GET,
          uri = fileTransferRequest.downloadUrl,
          headers = collection.immutable.Seq(
            RawHeader("x-request-id", fileTransferRequest.requestId.getOrElse("-")),
            RawHeader("x-conversation-id", fileTransferRequest.conversationId),
            RawHeader(
              "x-correlation-id",
              fileTransferRequest.correlationId.getOrElse(
                throw new IllegalArgumentException("Missing correlationId argument of FileTransferRequest")
              )
            )
          )
        )
        (fileDownloadHttpRequest, (fileTransferRequest, fileDownloadHttpRequest))
      }
      .via(connectionPool)
      .flatMapConcat {
        case (Success(fileDownloadHttpResponse), (fileTransferRequest, fileDownloadHttpRequest)) =>
          if (fileDownloadHttpResponse.status.isSuccess()) {

            val fileUploadHttpRequest: HttpRequest =
              createFileUploadHttpRequest(fileTransferRequest, fileDownloadHttpResponse.entity.dataBytes)

            val source: Source[(Try[HttpResponse], (FileTransferRequest, HttpRequest)), NotUsed] =
              Source
                .single((fileUploadHttpRequest, (fileTransferRequest, fileUploadHttpRequest)))
                .via(connectionPool)

            source
          } else
            Source
              .future(
                fileDownloadHttpResponse.entity
                  .toStrict(unitInterval * 1000)
                  .map(_.data.take(MAX_ERROR_HTTP_BODY_LENGTH).decodeString(StandardCharsets.UTF_8))(
                    actorSystem.dispatcher
                  )
              )
              .flatMapConcat(responseBody =>
                Source.single(
                  (
                    Failure(
                      FileDownloadFailure(
                        fileTransferRequest.applicationName,
                        fileTransferRequest.conversationId,
                        fileTransferRequest.correlationId.getOrElse(""),
                        fileTransferRequest.upscanReference,
                        fileDownloadHttpResponse.status.intValue(),
                        fileDownloadHttpResponse.status.reason(),
                        responseBody,
                        fileTransferRequest.attempt.getOrElse(0),
                        fileTransferRequest.durationMillis
                      )
                    ),
                    (fileTransferRequest, fileDownloadHttpRequest)
                  )
                )
              )

        case (Failure(fileDownloadError), (fileTransferRequest, fileDownloadHttpRequest)) =>
          Source.single(
            (
              Failure(
                FileDownloadException(
                  fileTransferRequest.applicationName,
                  fileTransferRequest.conversationId,
                  fileTransferRequest.correlationId
                    .getOrElse(""),
                  fileTransferRequest.upscanReference,
                  fileDownloadError,
                  fileTransferRequest.attempt.getOrElse(0),
                  fileTransferRequest.durationMillis
                )
              ),
              (fileTransferRequest, fileDownloadHttpRequest)
            )
          )
      }

  /** Akka Stream flow:
    *   - encodes data content stream using base64,
    *   - wraps base64 content in a json payload,
    *   - forwards to the upstream endpoint.
    */
  final val dataTransferFlow: Flow[
    FileTransferRequest,
    (Try[HttpResponse], (FileTransferRequest, HttpRequest)),
    NotUsed
  ] =
    Flow[FileTransferRequest]
      .map { fileTransferRequest =>
        Logger(getClass).info(
          s"Starting transfer requested by ${fileTransferRequest.applicationName} with conversationId=${fileTransferRequest.conversationId} [correlationId=${fileTransferRequest.correlationId
              .getOrElse("")}] of the data inlined in the URL, expected SHA-256 checksum is ${fileTransferRequest.checksum}, request startTime is ${fileTransferRequest.startTime} ..."
        )

        val dataBytes: Source[ByteString, NotUsed] =
          sourceFromDataURL(fileTransferRequest.downloadUrl)

        val fileUploadHttpRequest: HttpRequest =
          createFileUploadHttpRequest(fileTransferRequest, dataBytes)

        (fileUploadHttpRequest, (fileTransferRequest, fileUploadHttpRequest))
      }
      .via(connectionPool)

  final val dataUrlHandler: URLStreamHandler =
    new com.github.robtimus.net.protocol.data.Handler()

  final def sourceFromDataURL(url: String): Source[ByteString, NotUsed] =
    Source.single {
      val out = new ByteArrayOutputStream()
      val chunk: Array[Byte] = Array.ofDim[Byte](1024)
      Try {
        val in = new BufferedInputStream(new URL(null, url, dataUrlHandler).openStream())
        var bytesRead: Int = in.read(chunk)
        while (bytesRead > 0) {
          out.write(chunk, 0, bytesRead)
          bytesRead = in.read(chunk)
        }
        val bytes = ByteString(out.toByteArray())
        Try(in.close())
        Try(out.close())
        bytes
      }.transform(
        c => Success(c),
        t =>
          Failure(
            new Exception(
              s"Error while opening an URL starting with ${url.take(50)}",
              t
            )
          )
      ).get
    }

  /** Runs the flow for a single transfer request.
    */
  final def executeSingleFileTransfer[R](
    fileTransferRequest: FileTransferRequest,
    onComplete: (Int, Option[String], FileTransferRequest) => R,
    onFailure: (Throwable, FileTransferRequest) => R,
    zero: R
  ): Future[R] =
    Source
      .single(fileTransferRequest)
      .via(if (fileTransferRequest.isDataURL) dataTransferFlow else fileTransferFlow)
      .runFold[R](zero) {
        case (_, (Success(fileUploadHttpResponse), (fileTransferRequest, fileUploadHttpRequest))) =>
          if (fileUploadHttpResponse.status.isSuccess()) {
            fileUploadHttpResponse.entity.discardBytes()
            Logger(getClass).info(
              s"Transfer attempt ${fileTransferRequest.attempt.getOrElse(0) + 1} requested by ${fileTransferRequest.applicationName} with conversationId=${fileTransferRequest.conversationId} [correlationId=${fileTransferRequest.correlationId
                  .getOrElse("")}] of the file ${fileTransferRequest.upscanReference} has been successful, duration was ${fileTransferRequest.durationMillis} ms."
            )
            onComplete(fileUploadHttpResponse.status.intValue(), None, fileTransferRequest)
          } else {
            val httpBody: Option[String] = Await.result(
              fileUploadHttpResponse.entity
                .toStrict(unitInterval * 1000)
                .map { entity =>
                  val body = entity.data.take(10240).decodeString(StandardCharsets.UTF_8)
                  Logger(getClass).error(
                    s"Upload request requested by ${fileTransferRequest.applicationName} with conversationId=${fileTransferRequest.conversationId} [correlationId=${fileTransferRequest.correlationId
                        .getOrElse("")}] of the file ${fileTransferRequest.upscanReference} to ${fileUploadHttpRequest.uri} has failed with status ${fileUploadHttpResponse.status
                        .intValue()} beacuse of ${fileUploadHttpResponse.status.reason}, response body was [$body]; it was ${fileTransferRequest.attempt
                        .getOrElse(0) + 1} attempt, duration was ${fileTransferRequest.durationMillis} ms."
                  )
                  if (body.isEmpty) None else Some(body)
                }(actorSystem.dispatcher),
              unitInterval * 1000
            )
            onComplete(fileUploadHttpResponse.status.intValue(), httpBody, fileTransferRequest)
          }

        case (_, (Failure(error: FileDownloadException), (fileTransferRequest, fileUploadHttpRequest))) =>
          Logger(getClass).error(error.getMessage(), error.exception)
          onFailure(error, fileTransferRequest)

        case (_, (Failure(error: FileDownloadFailure), (fileTransferRequest, fileUploadHttpRequest))) =>
          Logger(getClass).error(error.getMessage())
          onFailure(error, fileTransferRequest)

        case (_, (Failure(uploadError), (fileTransferRequest, fileUploadHttpRequest))) =>
          val writer = new StringWriter()
          uploadError.printStackTrace(new PrintWriter(writer))
          val stackTrace = writer.getBuffer().toString()
          Logger(getClass).error(
            s"Upload request requested by ${fileTransferRequest.applicationName} with conversationId=${fileTransferRequest.conversationId} [correlationId=${fileTransferRequest.correlationId
                .getOrElse("")}] of the file ${fileTransferRequest.upscanReference} to ${fileUploadHttpRequest.uri} has failed because of [${uploadError.getClass
                .getName()}: ${uploadError.getMessage()}].\n$stackTrace; it was ${fileTransferRequest.attempt
                .getOrElse(0) + 1} attempt, duration was ${fileTransferRequest.durationMillis} ms."
          )
          onFailure(uploadError, fileTransferRequest)
      }

}

final case class FileDownloadException(
  applicationName: String,
  conversationId: String,
  correlationId: String,
  upscanReference: String,
  exception: Throwable,
  attempt: Int,
  durationMillis: Int
) extends Exception(
      s"Download requested by $applicationName with conversationId=$conversationId [correlationId=$correlationId] of the file $upscanReference has failed because of ${exception.getClass.getName}: ${exception
          .getMessage()}; it was ${attempt + 1} attempt, duration was $durationMillis ms."
    )
final case class FileDownloadFailure(
  applicationName: String,
  conversationId: String,
  correlationId: String,
  upscanReference: String,
  status: Int,
  reason: String,
  responseBody: String,
  attempt: Int,
  durationMillis: Int
) extends Exception(
      s"Download requested by $applicationName with conversationId=$conversationId [correlationId=$correlationId] of the file $upscanReference has failed with status $status $reason and response body [$responseBody]; it was ${attempt + 1} attempt, duration was $durationMillis ms."
    )
