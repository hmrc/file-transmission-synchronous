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

/**
  * A Flow modelling transfer of a single file (download and upload).
  */
trait FileTransferFlow {

  val appConfig: AppConfig
  val unitInterval: FiniteDuration

  implicit val materializer: Materializer
  implicit val actorSystem: ActorSystem

  private val connectionPool: Flow[
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
        case (Success(fileDownloadHttpResponse), (fileTransferRequest, fileDownloadHttpRequest)) =>
          if (fileDownloadHttpResponse.status.isSuccess()) {
            Logger(getClass).info(
              s"Starting transfer with [applicationName=${fileTransferRequest.applicationName}] and [conversationId=${fileTransferRequest.conversationId}] and [correlationId=${fileTransferRequest.correlationId
                .getOrElse("")}] of the file [${fileTransferRequest.upscanReference}], expected SHA-256 checksum is ${fileTransferRequest.checksum}, received http response status is ${fileDownloadHttpResponse.status} ..."
            )
            val jsonHeader = s"""{
                                |    "CaseReferenceNumber":"${fileTransferRequest.caseReferenceNumber}",
                                |    "ApplicationType":"${fileTransferRequest.applicationName}",
                                |    "OriginatingSystem":"Digital",
                                |    "Content":"""".stripMargin

            val jsonFooter = "\"\n}"

            val fileEncodeAndWrapSource: Source[ByteString, Future[FileSizeAndChecksum]] =
              fileDownloadHttpResponse.entity.dataBytes
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

            val fileUploadHttpRequest = HttpRequest(
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
                .single((fileUploadHttpRequest, (fileTransferRequest, fileUploadHttpRequest)))
                .via(connectionPool)

            source
          } else
            Source
              .future(
                fileDownloadHttpResponse.entity
                  .toStrict(unitInterval * 1000)
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
                        fileDownloadHttpResponse.status.intValue(),
                        fileDownloadHttpResponse.status.reason(),
                        responseBody,
                        fileTransferRequest.attempt.getOrElse(0)
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
                  fileTransferRequest.attempt.getOrElse(0)
                )
              ),
              (fileTransferRequest, fileDownloadHttpRequest)
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
        case (_, (Success(fileUploadHttpResponse), (fileTransferRequest, fileUploadHttpRequest))) =>
          val httpBody: Option[String] = Await.result(
            fileUploadHttpResponse.entity
              .toStrict(unitInterval * 1000)
              .map { entity =>
                val body = entity.data.take(10240).decodeString(StandardCharsets.UTF_8)
                if (fileUploadHttpResponse.status.isSuccess())
                  Logger(getClass).info(
                    s"Transfer attempt ${fileTransferRequest.attempt.getOrElse(0) + 1} requested by ${fileTransferRequest.applicationName} with conversationId=${fileTransferRequest.conversationId} [correlationId=${fileTransferRequest.correlationId
                      .getOrElse("")}] of the file ${fileTransferRequest.upscanReference} has been successful."
                  )
                else
                  Logger(getClass).error(
                    s"Upload request requested by ${fileTransferRequest.applicationName} with conversationId=${fileTransferRequest.conversationId} [correlationId=${fileTransferRequest.correlationId
                      .getOrElse("")}] of the file ${fileTransferRequest.upscanReference} to ${fileUploadHttpRequest.uri} has failed with status ${fileUploadHttpResponse.status
                      .intValue()} beacuse of ${fileUploadHttpResponse.status.reason}, response body was [$body]; it was ${fileTransferRequest.attempt
                      .getOrElse(0) + 1} attempt."
                  )
                if (body.isEmpty) None else Some(body)
              }(actorSystem.dispatcher),
            unitInterval * 1000
          )
          onComplete(fileUploadHttpResponse.status.intValue(), httpBody, fileTransferRequest)

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
              .getName()}: ${uploadError.getMessage()}].\n$stackTrace; it was ${fileTransferRequest.attempt.getOrElse(0) + 1} attempt."
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
  attempt: Int
) extends Exception(
      s"Download requested by $applicationName with conversationId=$conversationId [correlationId=$correlationId] of the file $upscanReference has failed because of ${exception.getClass.getName}: ${exception
        .getMessage()}; it was ${attempt + 1} attempt."
    )
final case class FileDownloadFailure(
  applicationName: String,
  conversationId: String,
  correlationId: String,
  upscanReference: String,
  status: Int,
  reason: String,
  responseBody: String,
  attempt: Int
) extends Exception(
      s"Download requested by $applicationName with conversationId=$conversationId [correlationId=$correlationId] of the file $upscanReference has failed with status $status $reason and response body [$responseBody]; it was ${attempt + 1} attempt."
    )
