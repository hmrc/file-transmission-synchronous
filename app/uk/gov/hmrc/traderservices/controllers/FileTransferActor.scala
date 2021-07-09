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

import akka.actor.Actor
import akka.actor.ActorRef
import akka.pattern.pipe
import play.api.Logger
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.traderservices.models._

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class FileTransferActor(
  conversationId: String,
  caseReferenceNumber: String,
  applicationName: String,
  requestId: String,
  transfer: FileTransferActor.TransferFunction,
  audit: FileTransferActor.AuditFunction
) extends Actor {

  import FileTransferActor._
  import context.dispatcher

  var results: Seq[FileTransferResult] = Seq.empty
  var clientRef: ActorRef = ActorRef.noSender
  var startTimestamp: Long = 0

  override def receive: Receive = {
    case TransferMultipleFiles(files, batchSize) =>
      startTimestamp = System.currentTimeMillis()
      clientRef = sender()
      files
        .map {
          case (file, index) =>
            TransferSingleFile(file, index, batchSize)
        }
        .foreach(request => self ! request)
      self ! CheckComplete(batchSize)

    case TransferSingleFile(file, index, batchSize) =>
      transfer(
        FileTransferRequest(
          conversationId,
          caseReferenceNumber,
          applicationName,
          file.upscanReference,
          file.downloadUrl,
          file.checksum,
          file.fileName,
          file.fileMimeType,
          batchSize,
          index + 1,
          Some(UUID.randomUUID().toString()),
          Some(requestId),
          file.fileSize
        ),
        (httpStatus: Int, httpBody: Option[String], fileTransferRequest: FileTransferRequest) =>
          FileTransferResult(
            fileTransferRequest.upscanReference,
            isSuccess(httpStatus),
            httpStatus,
            LocalDateTime.now,
            if (isSuccess(httpStatus)) None else httpBody
          ),
        (error: Throwable, fileTransferRequest: FileTransferRequest) =>
          error match {
            case fileDownloadFailure: FileDownloadFailure =>
              FileTransferResult(
                fileTransferRequest.upscanReference,
                false,
                fileDownloadFailure.status,
                LocalDateTime.now,
                Option(fileDownloadFailure.responseBody)
              )

            case fileDownloadException: FileDownloadException =>
              FileTransferResult(
                fileTransferRequest.upscanReference,
                false,
                0,
                LocalDateTime.now,
                Option(
                  s"${fileDownloadException.exception.getClass().getName()}: ${fileDownloadException.exception.getMessage()}"
                )
              )

            case _ =>
              FileTransferResult(
                fileTransferRequest.upscanReference,
                false,
                0,
                LocalDateTime.now,
                Option(s"${error.getClass().getName()}: ${error.getMessage()}")
              )
          },
        FileTransferResult.empty
      )
        .pipeTo(sender())

    case result: FileTransferResult =>
      results = results :+ result

    case akka.actor.Status.Failure(error) =>
      Logger(getClass).error(error.toString())
      results = results :+ FileTransferResult(
        upscanReference = "<unknown>",
        success = false,
        httpStatus = 0,
        LocalDateTime.now(),
        error = Some(error.toString())
      )

    case CheckComplete(batchSize) =>
      if (results.size == batchSize || System.currentTimeMillis() - startTimestamp > 3600000 /*hour*/ ) {
        clientRef ! results
        audit(results)
        context.stop(self)
        Logger(getClass).info(
          s"Transferred ${results.size} out of $batchSize files in ${(System
            .currentTimeMillis() - startTimestamp) / 1000} seconds. It was ${results
            .count(_.success)} successes and ${results.count(f => !f.success)} failures."
        )
      } else
        context.system.scheduler
          .scheduleOnce(FiniteDuration(500, "ms"), self, CheckComplete(batchSize))
  }

  final def isSuccess(status: Int): Boolean =
    status >= 200 && status < 300
}

object FileTransferActor {

  type TransferFunction = (
    FileTransferRequest,
    (Int, Option[String], FileTransferRequest) => FileTransferResult,
    (Throwable, FileTransferRequest) => FileTransferResult,
    FileTransferResult
  ) => Future[FileTransferResult]

  type AuditFunction =
    Seq[FileTransferResult] => Future[Unit]

  case class TransferMultipleFiles(files: Seq[(FileTransferData, Int)], batchSize: Int)
  case class TransferSingleFile(file: FileTransferData, index: Int, batchSize: Int)
  case class CheckComplete(batchSize: Int)
}
