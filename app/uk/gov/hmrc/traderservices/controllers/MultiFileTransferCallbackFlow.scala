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
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import play.api.Logger
import uk.gov.hmrc.traderservices.models._

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import play.api.libs.json.Json
import akka.util.ByteString
import java.nio.charset.StandardCharsets

/**
  * A Flow modelling callback request.
  */
trait MultiFileTransferCallbackFlow {

  implicit val materializer: Materializer
  implicit val actorSystem: ActorSystem

  private val connectionPool: Flow[
    (HttpRequest, (MultiFileTransferCallbackRequest, HttpRequest)),
    (Try[HttpResponse], (MultiFileTransferCallbackRequest, HttpRequest)),
    NotUsed
  ] = Http()
    .superPool[(MultiFileTransferCallbackRequest, HttpRequest)]()

  final val callbackFlow: Flow[
    MultiFileTransferCallbackRequest,
    (Try[HttpResponse], Either[(String, Boolean), Unit]),
    NotUsed
  ] =
    Flow[MultiFileTransferCallbackRequest]
      .map { callbackRequest =>
        val httpRequest = HttpRequest(
          method = HttpMethods.POST,
          uri = callbackRequest.callbackUrl,
          headers = collection.immutable.Seq(
            RawHeader("x-conversation-id", callbackRequest.result.conversationId)
          ),
          entity = HttpEntity
            .apply(
              ContentTypes.`application/json`,
              ByteString.fromString(Json.stringify(Json.toJson(callbackRequest.result)), StandardCharsets.UTF_8)
            )
        )
        (httpRequest, (callbackRequest, httpRequest))
      }
      .via(connectionPool)
      .map {
        case (t @ Success(callbackHttpResponse), (callbackRequest, callbackHttpRequest)) =>
          callbackHttpResponse.entity.discardBytes()
          if (callbackHttpResponse.status.isSuccess()) {
            Logger(getClass).info(
              s"Successfully called back ${callbackRequest.result.applicationName} with conversationId=${callbackRequest.result.conversationId}"
            )
            (t, Right(()))
          } else {
            val msg =
              s"Failure, received ${callbackHttpResponse.status.intValue()} while calling back ${callbackRequest.result.applicationName} with conversationId=${callbackRequest.result.conversationId}"
            Logger(getClass).error(msg)
            val shouldRetry = Retry.shouldRetry(callbackHttpResponse.status.intValue())
            (t, Left((msg, shouldRetry)))
          }

        case (t @ Failure(callbackError), (callbackRequest, callbackHttpRequest)) =>
          val msg =
            s"Failure, got $callbackError while calling back ${callbackRequest.result.applicationName} with conversationId=${callbackRequest.result.conversationId}"
          Logger(getClass).error(msg)
          (t, Left((msg, false)))
      }

  def executeCallback(callbackUrl: String, result: MultiFileTransferResult): Future[Either[(String, Boolean), Unit]] =
    Source
      .single(MultiFileTransferCallbackRequest(callbackUrl, result))
      .via(callbackFlow)
      .runFold[Either[(String, Boolean), Unit]](Right(())) {
        case (_, (_, result)) => result
      }

}
