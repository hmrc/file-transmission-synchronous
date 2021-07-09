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
import uk.gov.hmrc.traderservices.wiring.AppConfig

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.concurrent.Await

trait FileTransferFlow {

  val appConfig: AppConfig
  val httpResponseProcessingTimeout: FiniteDuration

  implicit val materializer: Materializer
  implicit val actorSystem: ActorSystem

  final val connectionPool: Flow[
    (HttpRequest, (FileTransferRequest, HttpRequest)),
    (Try[HttpResponse], (FileTransferRequest, HttpRequest)),
    NotUsed
  ] = Http()
    .superPool[(FileTransferRequest, HttpRequest)]()

  /**
    * Akka Stream flow:
    * - requests downloading the file,
    * - encodes file content stream using base64,
    * - wraps base64 content in a json payload,
    * - forwards to the upstream endpoint.
    */
  final val fileTransferFlow: Flow[
    FileTransferRequest,
    (Try[HttpResponse], (FileTransferRequest, HttpRequest)),
    NotUsed
  ] =
    Flow[FileTransferRequest]
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
        case (Success(fileDownloadResponse), (fileTransferRequest, fileDownloadRequest)) =>
          if (fileDownloadResponse.status.isSuccess()) {
            Logger(getClass).info(
              s"Starting transfer with [applicationName=${fileTransferRequest.applicationName}] and [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
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
                RawHeader("x-metadata", xmlMetadata),
                RawHeader("referer", fileTransferRequest.applicationName)
              ),
              entity = HttpEntity.apply(ContentTypes.`application/json`, fileEncodeAndWrapSource)
            )

            val source: Source[(Try[HttpResponse], (FileTransferRequest, HttpRequest)), NotUsed] =
              Source
                .single((eisUploadRequest, (fileTransferRequest, eisUploadRequest)))
                .via(connectionPool)

            source
          } else
            Source
              .future(
                fileDownloadResponse.entity
                  .toStrict(httpResponseProcessingTimeout)
                  .map(_.data.take(10240).decodeString(StandardCharsets.UTF_8))(actorSystem.dispatcher)
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
                        fileDownloadResponse.status.intValue(),
                        fileDownloadResponse.status.reason(),
                        responseBody
                      )
                    ),
                    (fileTransferRequest, fileDownloadRequest)
                  )
                )
              )

        case (Failure(fileDownloadError), (fileTransferRequest, fileDownloadRequest)) =>
          Source.single(
            (
              Failure(
                FileDownloadException(
                  fileTransferRequest.applicationName,
                  fileTransferRequest.conversationId,
                  fileTransferRequest.correlationId
                    .getOrElse(""),
                  fileTransferRequest.upscanReference,
                  fileDownloadError
                )
              ),
              (fileTransferRequest, fileDownloadRequest)
            )
          )
      }

  final def executeSingleFileTransfer[R](
    fileTransferRequest: FileTransferRequest,
    onComplete: (Int, Option[String], FileTransferRequest) => R,
    onFailure: (Throwable, FileTransferRequest) => R,
    zero: R
  ): Future[R] =
    Source
      .single(fileTransferRequest)
      .via(fileTransferFlow)
      .runFold[R](zero) {
        case (_, (Success(fileUploadResponse), (fileTransferRequest, eisUploadRequest))) =>
          val httpBody: Option[String] = Await.result(
            fileUploadResponse.entity
              .toStrict(httpResponseProcessingTimeout)
              .map { entity =>
                val body = entity.data.take(10240).decodeString(StandardCharsets.UTF_8)
                if (fileUploadResponse.status.isSuccess())
                  Logger(getClass).info(
                    s"Transfer [applicationName=${fileTransferRequest.applicationName}] and [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
                      .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}] succeeded."
                  )
                else
                  Logger(getClass).error(
                    s"Upload request with [applicationName=${fileTransferRequest.applicationName}] and [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
                      .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}] to [${eisUploadRequest.uri}] failed with status [${fileUploadResponse.status
                      .intValue()}], reason [${fileUploadResponse.status.reason}] and response body [$body]."
                  )
                if (body.isEmpty) None else Some(body)
              }(actorSystem.dispatcher),
            httpResponseProcessingTimeout
          )
          onComplete(fileUploadResponse.status.intValue(), httpBody, fileTransferRequest)

        case (_, (Failure(error: FileDownloadException), (fileTransferRequest, eisUploadRequest))) =>
          Logger(getClass).error(error.getMessage(), error.exception)
          onFailure(error, fileTransferRequest)

        case (_, (Failure(error: FileDownloadFailure), (fileTransferRequest, eisUploadRequest))) =>
          Logger(getClass).error(error.getMessage())
          onFailure(error, fileTransferRequest)

        case (_, (Failure(uploadError), (fileTransferRequest, eisUploadRequest))) =>
          val writer = new StringWriter()
          uploadError.printStackTrace(new PrintWriter(writer))
          val stackTrace = writer.getBuffer().toString()
          Logger(getClass).error(
            s"Upload request with [applicationName=${fileTransferRequest.applicationName}] and [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
              .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}] to [${eisUploadRequest.uri}] failed because of [${uploadError.getClass
              .getName()}: ${uploadError.getMessage()}].\n$stackTrace"
          )
          onFailure(uploadError, fileTransferRequest)
      }

}

final case class FileDownloadException(
  applicationName: String,
  conversationId: String,
  correlationId: String,
  upscanReference: String,
  exception: Throwable
) extends Exception(
      s"Download request with [applicationName=$applicationName] and [conversationId=$conversationId] and [correlationId=$correlationId] of the file [$upscanReference] failed because of [${exception.getClass.getName}: ${exception
        .getMessage()}]."
    )
final case class FileDownloadFailure(
  applicationName: String,
  conversationId: String,
  correlationId: String,
  upscanReference: String,
  status: Int,
  reason: String,
  responseBody: String
) extends Exception(
      s"Download request with [applicationName=$applicationName] and [conversationId=$conversationId] and [correlationId=$correlationId] of the file [$upscanReference] failed with status [$status $reason] and response body [$responseBody]."
    )
