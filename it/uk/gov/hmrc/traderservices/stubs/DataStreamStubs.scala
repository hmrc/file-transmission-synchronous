package uk.gov.hmrc.traderservices.stubs

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.traderservices.services.TraderServicesAuditEvent.TraderServicesAuditEvent
import uk.gov.hmrc.traderservices.support.WireMockSupport
import play.api.libs.json.JsObject

trait DataStreamStubs extends Eventually {
  me: WireMockSupport =>

  override implicit val patienceConfig =
    PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(500, Millis)))

  def verifyAuditRequestSent(
    count: Int,
    event: TraderServicesAuditEvent,
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

  def verifyAuditRequestNotSent(event: TraderServicesAuditEvent): Unit =
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
