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

package uk.gov.hmrc.traderservices.controllers

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Flow
import scala.util.Try
import akka.NotUsed
import akka.http.scaladsl.model.HttpResponse
import scala.util.Success
import scala.util.Failure
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.headers.Date
import akka.http.scaladsl.model.DateTime
import uk.gov.hmrc.traderservices.models._
import akka.http.scaladsl.Http
import akka.actor.ActorSystem
import uk.gov.hmrc.traderservices.wiring.AppConfig
import akka.stream.scaladsl.Source
import scala.concurrent.Future
import play.api.mvc.Result
import play.api.mvc.Results._
import akka.util.ByteString
import play.api.Logger
import akka.stream.Materializer
import scala.concurrent.duration.FiniteDuration
import java.nio.charset.StandardCharsets
import java.io.StringWriter
import java.io.PrintWriter

trait FileTransferFlow {

  val appConfig: AppConfig
  implicit val materializer: Materializer
  implicit val actorSystem: ActorSystem

  final val connectionPool: Flow[
    (HttpRequest, (TraderServicesFileTransferRequest, HttpRequest)),
    (Try[HttpResponse], (TraderServicesFileTransferRequest, HttpRequest)),
    NotUsed
  ] = Http()
    .superPool[(TraderServicesFileTransferRequest, HttpRequest)]()

  /**
    * Akka Stream flow:
    * - requests downloading the file,
    * - encodes file content stream using base64,
    * - wraps base64 content in a json payload,
    * - forwards to the upstream endpoint.
    */
  final val fileTransferFlow: Flow[
    TraderServicesFileTransferRequest,
    (Try[HttpResponse], (TraderServicesFileTransferRequest, HttpRequest)),
    NotUsed
  ] =
    Flow[TraderServicesFileTransferRequest]
      .map { fileTransferRequest =>
        val httpRequest = HttpRequest(
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
        (httpRequest, (fileTransferRequest, httpRequest))
      }
      .via(connectionPool)
      .flatMapConcat {
        case (Success(fileDownloadResponse), (fileTransferRequest, _)) =>
          if (fileDownloadResponse.status.isSuccess()) {
            Logger(getClass).info(
              s"Starting transfer with [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
                .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}], expected SHA-256 checksum is ${fileTransferRequest.checksum}, received http response status is ${fileDownloadResponse.status} ..."
            )
            val jsonHeader = s"""{
                                |    "CaseReferenceNumber":"${fileTransferRequest.caseReferenceNumber}",
                                |    "ApplicationType":"${fileTransferRequest.applicationName}",
                                |    "OriginatingSystem":"Digital",
                                |    "Content":"""".stripMargin

            val jsonFooter = "\"\n}"

            val fileEncodeAndWrapSource: Source[ByteString, Future[FileSizeAndChecksum]] =
              fileDownloadResponse.entity.dataBytes
                .viaMat(EncodeFileBase64)(Keep.right)
                .prepend(Source.single(ByteString(jsonHeader, StandardCharsets.UTF_8)))
                .concat(Source.single(ByteString(jsonFooter, StandardCharsets.UTF_8)))

            val xmlMetadata = FileTransferMetadataHeader(
              caseReferenceNumber = fileTransferRequest.caseReferenceNumber,
              applicationName = fileTransferRequest.applicationName,
              correlationId = fileTransferRequest.correlationId.getOrElse(""),
              conversationId = fileTransferRequest.conversationId,
              sourceFileName = fileTransferRequest.fileName,
              sourceFileMimeType = fileTransferRequest.fileMimeType,
              fileSize = fileTransferRequest.fileSize.getOrElse(1024),
              checksum = fileTransferRequest.checksum,
              batchSize = fileTransferRequest.batchSize,
              batchCount = fileTransferRequest.batchCount
            ).toXmlString

            val eisUploadRequest = HttpRequest(
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
                RawHeader("x-metadata", xmlMetadata)
              ),
              entity = HttpEntity.apply(ContentTypes.`application/json`, fileEncodeAndWrapSource)
            )

            val source: Source[(Try[HttpResponse], (TraderServicesFileTransferRequest, HttpRequest)), NotUsed] =
              Source
                .single((eisUploadRequest, (fileTransferRequest, eisUploadRequest)))
                .via(connectionPool)

            source
          } else
            Source
              .fromFuture(
                fileDownloadResponse.entity
                  .toStrict(FiniteDuration(10000, "ms"))
                  .map(_.data.take(1024).decodeString(StandardCharsets.UTF_8))(actorSystem.dispatcher)
              )
              .flatMapConcat(responseBody =>
                Source.failed(
                  FileDownloadFailure(
                    fileTransferRequest.conversationId,
                    fileTransferRequest.correlationId.getOrElse(""),
                    fileTransferRequest.upscanReference,
                    fileDownloadResponse.status.intValue(),
                    fileDownloadResponse.status.reason(),
                    responseBody
                  )
                )
              )

        case (Failure(fileDownloadError), (fileTransferRequest, _)) =>
          Source
            .failed(
              FileDownloadException(
                fileTransferRequest.conversationId,
                fileTransferRequest.correlationId
                  .getOrElse(""),
                fileTransferRequest.upscanReference,
                fileDownloadError
              )
            )
      }

  final def executeSingleFileTransfer(
    fileTransferRequest: TraderServicesFileTransferRequest
  ): Future[Result] =
    Source
      .single(fileTransferRequest)
      .via(fileTransferFlow)
      .runFold[Result](Ok) {
        case (_, (Success(fileUploadResponse), (fileTransferRequest, eisUploadRequest))) =>
          if (fileUploadResponse.status.isSuccess()) {
            fileUploadResponse.discardEntityBytes()
            Logger(getClass).info(
              s"Transfer [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
                .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}] succeeded."
            )
          } else
            fileUploadResponse.entity
              .toStrict(FiniteDuration(10000, "ms"))
              .foreach { entity =>
                Logger(getClass).error(
                  s"Upload request with [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
                    .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}] to [${eisUploadRequest.uri}] failed with status [${fileUploadResponse.status
                    .intValue()}], reason [${fileUploadResponse.status.reason}] and response body [${entity.data
                    .take(1024)
                    .decodeString(StandardCharsets.UTF_8)}]."
                )
              }(actorSystem.dispatcher)

          Status(fileUploadResponse.status.intValue())

        case (_, (Failure(error: FileDownloadException), _)) =>
          Logger(getClass).error(error.getMessage(), error.exception)
          InternalServerError

        case (_, (Failure(error: FileDownloadFailure), _)) =>
          Logger(getClass).error(error.getMessage())
          InternalServerError

        case (_, (Failure(uploadError), (fileTransferRequest, eisUploadRequest))) =>
          val writer = new StringWriter()
          uploadError.printStackTrace(new PrintWriter(writer))
          val stackTrace = writer.getBuffer().toString()
          Logger(getClass).error(
            s"Upload request with [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
              .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}] to [${eisUploadRequest.uri}] failed because of [${uploadError.getClass
              .getName()}: ${uploadError.getMessage()}].\n$stackTrace"
          )
          InternalServerError
      }

}

final case class FileDownloadException(
  conversationId: String,
  correlationId: String,
  upscanReference: String,
  exception: Throwable
) extends Exception(
      s"Download request with [conversationId=$conversationId] and [correlationId=$correlationId] of the file [$upscanReference] failed because of [${exception.getClass.getName}: ${exception.getMessage()}]."
    )
final case class FileDownloadFailure(
  conversationId: String,
  correlationId: String,
  upscanReference: String,
  status: Int,
  reason: String,
  responseBody: String
) extends Exception(
      s"Download request with [conversationId=$conversationId] and [correlationId=$correlationId] of the file [$upscanReference] failed with status [$status $reason] and response body [$responseBody]."
    )
