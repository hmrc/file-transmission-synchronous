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

import akka.stream.stage.GraphStageWithMaterializedValue
import akka.stream.FlowShape
import akka.util.ByteString
import scala.concurrent.Future
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.stage.GraphStageLogic
import akka.stream.Attributes
import akka.stream.stage.OutHandler
import akka.stream.stage.StageLogging
import akka.stream.stage.InHandler
import scala.concurrent.Promise
import java.util.Base64
import scala.util.Failure
import java.security.MessageDigest
import scala.util.Success
import java.nio.ByteBuffer
import play.api.Logger

case class FileSizeAndChecksum(fileSize: Int, checkumSHA256: String)

/**
  * Custom Akka Stream stage encoding stream as base64
  * and calculating size and SHA-256 checksum.
  */
object EncodeFileBase64
    extends GraphStageWithMaterializedValue[FlowShape[ByteString, ByteString], Future[
      FileSizeAndChecksum
    ]] {

  val in = Inlet[ByteString]("EncodeFileBase64.in")
  val out = Outlet[ByteString]("EncodeFileBase64.out")

  override val shape = FlowShape.of(in, out)

  override def createLogicAndMaterializedValue(
    attr: Attributes
  ): (GraphStageLogic, Future[FileSizeAndChecksum]) = {

    val promise = Promise[FileSizeAndChecksum]()

    val stageLogic =
      new GraphStageLogic(shape) with StageLogging {

        val t0 = System.nanoTime()

        val encoder = Base64.getEncoder()
        val digest = MessageDigest.getInstance("SHA-256")
        var fileSize: Int = 0
        var previous: ByteBuffer = ByteBuffer.allocate(0)

        final override def preStart: Unit =
          setKeepGoing(true)

        setHandler(
          in,
          new InHandler {
            final override def onPush(): Unit = {
              val input = grab(in)
              encodeAndPush(input)
            }

            final override def onUpstreamFinish(): Unit =
              if (isAvailable(out)) finish()
              else
                setHandler(
                  out,
                  new OutHandler {
                    override def onPull(): Unit =
                      finish()
                  }
                )

            private def encodeAndPush(input: ByteString): Unit = {
              val bytes = (if (previous.remaining > 0) (ByteString(previous) ++ input) else input).toByteBuffer
              val length = bytes.limit()
              val chunkLength = if (length < 3) length else (length / 3) * 3
              fileSize = fileSize + chunkLength
              val chunk = Array.ofDim[Byte](chunkLength)
              bytes.get(chunk)
              digest.update(chunk)
              val message = encoder.encode(chunk)
              previous = bytes
              push(out, ByteString(message))
            }

            def finish(): Unit = {
              encodeAndPush(ByteString.empty)
              if (!promise.isCompleted) {
                val checksum = convertBytesToHex(digest.digest())
                Logger(getClass).info(
                  s"Stream encoding success, size $fileSize bytes, SHA-256 checksum $checksum, time ${(System
                    .nanoTime() - t0) / 10e6} ms."
                )
                promise.complete(
                  Success(FileSizeAndChecksum(fileSize, checksum))
                )
              }
              super.onUpstreamFinish()
            }

            final override def onUpstreamFailure(ex: Throwable): Unit = {
              if (!promise.isCompleted) {
                Logger(getClass).error(s"Stream encoding failed because of ${ex.getMessage()}.")
                promise.complete(
                  Failure(ex)
                )
              }
              super.onUpstreamFailure(ex)
            }
          }
        )

        setHandler(
          out,
          new OutHandler {
            override def onPull(): Unit =
              pull(in)
          }
        )
      }

    (stageLogic, promise.future)

  }

  private def convertBytesToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes)
      sb.append(String.format("%02x", Byte.box(b)))
    sb.toString
  }

}
