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

import akka.actor.Actor
import akka.actor.ActorRef
import akka.pattern.pipe
import play.api.Logger
import uk.gov.hmrc.traderservices.models._

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json.JsObject

/**
  * An Actor responsible for orchestrating transmission of files.
  *
  * @param conversationId unique conversation ID of this transfer batch
  * @param caseReferenceNumber
  * @param applicationName
  * @param requestId
  * @param transfer function to call to transfer a single file
  * @param audit function to call to audit result of transmission
  * @param callback function to call to after transmission
  */
class FileTransferActor(
  conversationId: String,
  caseReferenceNumber: String,
  applicationName: String,
  metadata: Option[JsObject],
  requestId: String,
  transfer: FileTransferActor.TransferFunction,
  audit: FileTransferActor.AuditFunction,
  callback: FileTransferActor.CallbackFunction,
  unitInterval: FiniteDuration
) extends Actor {

  val UNKNOWN = "<unknown>"

  import FileTransferActor._
  import context.dispatcher

  val MAX_RETRY_ATTEMPTS = 2

  var results: Seq[FileTransferResult] = Seq.empty
  var clientRef: ActorRef = ActorRef.noSender
  var startTimestamp: Long = 0

  override def receive: Receive = {
    case TransferMultipleFiles(files, batchSize) =>
      startTimestamp = System.nanoTime()
      clientRef = sender()
      files
        .map {
          case (file, index) =>
            TransferSingleFile(file, index, batchSize, 0)
        }
        .foreach(request => self ! request)
      context.system.scheduler
        .scheduleOnce(unitInterval, self, CheckComplete(batchSize))

    case thisMessage @ TransferSingleFile(file, index, batchSize, attempt) =>
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
          file.fileSize,
          Some(attempt)
        ),
        (httpStatus: Int, httpBody: Option[String], fileTransferRequest: FileTransferRequest) =>
          if (Retry.shouldRetry(httpStatus) && attempt < MAX_RETRY_ATTEMPTS)
            Left(thisMessage.copy(attempt = attempt + 1))
          else
            Right(
              FileTransferResult(
                fileTransferRequest.upscanReference,
                fileTransferRequest.checksum,
                fileTransferRequest.fileName,
                fileTransferRequest.fileMimeType,
                fileTransferRequest.fileSize.getOrElse(0),
                isSuccess(httpStatus),
                httpStatus,
                LocalDateTime.now,
                fileTransferRequest.correlationId.getOrElse("<undefined>"),
                fileTransferRequest.durationMillis,
                if (isSuccess(httpStatus)) None else httpBody
              )
            ),
        (error: Throwable, fileTransferRequest: FileTransferRequest) =>
          error match {
            case fileDownloadFailure: FileDownloadFailure =>
              if (Retry.shouldRetry(fileDownloadFailure.status) && attempt < MAX_RETRY_ATTEMPTS)
                Left(thisMessage.copy(attempt = attempt + 1))
              else
                Right(
                  FileTransferResult(
                    fileTransferRequest.upscanReference,
                    fileTransferRequest.checksum,
                    fileTransferRequest.fileName,
                    fileTransferRequest.fileMimeType,
                    fileTransferRequest.fileSize.getOrElse(0),
                    false,
                    fileDownloadFailure.status,
                    LocalDateTime.now,
                    fileTransferRequest.correlationId.getOrElse(UNKNOWN),
                    fileTransferRequest.durationMillis,
                    Option(fileDownloadFailure.responseBody)
                  )
                )

            case fileDownloadException: FileDownloadException =>
              Right(
                FileTransferResult(
                  fileTransferRequest.upscanReference,
                  fileTransferRequest.checksum,
                  fileTransferRequest.fileName,
                  fileTransferRequest.fileMimeType,
                  fileTransferRequest.fileSize.getOrElse(0),
                  false,
                  0,
                  LocalDateTime.now,
                  fileTransferRequest.correlationId.getOrElse(UNKNOWN),
                  fileTransferRequest.durationMillis,
                  Option(
                    s"${fileDownloadException.exception.getClass().getName()}: ${fileDownloadException.exception.getMessage()}"
                  )
                )
              )

            case _ =>
              Right(
                FileTransferResult(
                  fileTransferRequest.upscanReference,
                  fileTransferRequest.checksum,
                  fileTransferRequest.fileName,
                  fileTransferRequest.fileMimeType,
                  fileTransferRequest.fileSize.getOrElse(0),
                  false,
                  0,
                  LocalDateTime.now,
                  fileTransferRequest.correlationId.getOrElse(UNKNOWN),
                  fileTransferRequest.durationMillis,
                  Option(s"${error.getClass().getName()}: ${error.getMessage()}")
                )
              )
          },
        Right(FileTransferResult.empty)
      )
        .pipeTo(self)

    case Right(result: FileTransferResult) =>
      results = results :+ result

    case Left(retryMessage: TransferSingleFile) =>
      context.system.scheduler
        .scheduleOnce(unitInterval * retryMessage.attempt * 10, self, retryMessage)

    case akka.actor.Status.Failure(error) =>
      Logger(getClass).error(error.toString())
      results = results :+ FileTransferResult(
        upscanReference = UNKNOWN,
        checksum = UNKNOWN,
        fileName = UNKNOWN,
        fileMimeType = UNKNOWN,
        fileSize = 0,
        success = false,
        httpStatus = 0,
        LocalDateTime.now(),
        UNKNOWN,
        0,
        error = Some(error.toString())
      )

    case CheckComplete(batchSize) =>
      val totalDurationMillis: Int = ((System.nanoTime() - startTimestamp) / 1000000).toInt
      val completed = results.size == batchSize
      val timeout = FiniteDuration(totalDurationMillis, "ms").gt(unitInterval * 36000)
      if (completed || timeout) {
        val response = MultiFileTransferResult(
          conversationId,
          caseReferenceNumber,
          applicationName,
          results,
          totalDurationMillis,
          metadata
        )
        clientRef ! response
        audit(results)
        self ! Callback(response, 0)
        if (completed)
          Logger(getClass).info(
            s"Transferred ${results.size} out of $batchSize files, it was ${results
              .count(_.success)} successes and ${results.count(f => !f.success)} failures, total duration was $totalDurationMillis ms."
          )
        else
          Logger(getClass).error(
            s"Timeout, transferred ${if (results.nonEmpty) "none"
            else s"only ${results.size}"} out of $batchSize files, ${if (results.nonEmpty) s"it was ${results
              .count(_.success)} successes and ${results.count(f => !f.success)} failures.}, total duration was $totalDurationMillis ms."
            else ""}"
          )
      } else
        context.system.scheduler
          .scheduleOnce(unitInterval, self, CheckComplete(batchSize))

    case Callback(response, attempt) =>
      callback(response)
        .map {
          case Right(()) =>
            Completed

          case Left((error, retry)) =>
            if (retry && attempt < MAX_RETRY_ATTEMPTS)
              context.system.scheduler
                .scheduleOnce(unitInterval * 10, self, Callback(response, attempt + 1))
            else
              Completed
        }
        .pipeTo(self)

    case Completed =>
      context.stop(self)

  }

  final def isSuccess(status: Int): Boolean =
    status >= 200 && status < 300
}

object FileTransferActor {

  type TransferResult = Either[TransferSingleFile, FileTransferResult]

  type TransferFunction = (
    FileTransferRequest,
    (Int, Option[String], FileTransferRequest) => TransferResult,
    (Throwable, FileTransferRequest) => TransferResult,
    TransferResult
  ) => Future[TransferResult]

  type AuditFunction =
    Seq[FileTransferResult] => Future[Unit]

  type CallbackFunction =
    MultiFileTransferResult => Future[Either[(String, Boolean), Unit]]

  case class TransferMultipleFiles(files: Seq[(FileTransferData, Int)], batchSize: Int)
  case class TransferSingleFile(file: FileTransferData, index: Int, batchSize: Int, attempt: Int)
  case class CheckComplete(batchSize: Int)
  case class Callback(response: MultiFileTransferResult, attempt: Int)
  case object Completed
}
