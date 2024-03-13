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

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.JsObject
import uk.gov.hmrc.traderservices.services.FileTransmissionAuditEvent.FileTransmissionAuditEvent
import uk.gov.hmrc.traderservices.support.WireMockSupport

trait DataStreamStubs extends Eventually {
  me: WireMockSupport =>

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(500, Millis)))

  def verifyAuditRequestSent(
    count: Int,
    event: FileTransmissionAuditEvent
  ): Unit =
    eventually {
      verify(
        count,
        postRequestedFor(urlPathMatching(auditUrl))
          .withRequestBody(
            similarToJson(s"""{
          |  "auditSource": "file-transmission-synchronous",
          |  "auditType": "$event"
          |}""")
          )
      )
    }

  def verifyAuditRequestSent(
    count: Int,
    event: FileTransmissionAuditEvent,
    details: JsObject,
    tags: Map[String, String] = Map.empty
  ): Unit =
    eventually {
      verify(
        count,
        postRequestedFor(urlPathMatching(auditUrl))
          .withRequestBody(
            similarToJson(s"""{
          |  "auditSource": "file-transmission-synchronous",
          |  "auditType": "$event",
          |  "tags": ${Json.toJson(tags)},
          |  "detail": ${Json.stringify(details)}
          |}""")
          )
      )
    }

  def verifyAuditRequestNotSent(event: FileTransmissionAuditEvent): Unit =
    eventually {
      verify(
        0,
        postRequestedFor(urlPathMatching(auditUrl))
          .withRequestBody(similarToJson(s"""{
          |  "auditSource": "file-transmission-synchronous",
          |  "auditType": "$event"
          |}"""))
      )
    }

  def givenAuditConnector(): Unit =
    stubFor(post(urlPathMatching(auditUrl)).willReturn(aResponse().withStatus(204)))

  private def auditUrl = "/write/audit.*"

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

}
