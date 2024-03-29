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

package uk.gov.hmrc.traderservices.controllers

import play.api.mvc.Results._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, InsufficientEnrolments}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionKeys}
import uk.gov.hmrc.traderservices.support.AppBaseISpec
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class AuthActionsISpec extends AppBaseISpec {

  object TestController extends AuthActions {

    override def authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]

    override val appConfig: AppConfig = new AppConfig {
      override val appName: String = "???"
      override val authBaseUrl: String = "???"
      override val authorisedServiceName: String = "HMRC-XYZ"
      override val authorisedIdentifierKey: String = "XYZNumber"
      override val eisBaseUrl: String = "???"
      override val eisAuthorizationToken: String = "???"
      override val eisEnvironment: String = "???"
      override val eisFileTransferHost: String = "???"
      override val eisFileTransferPort: Int = -1
      override val eisFileTransferApiPath: String = "???"
      override val unitInterval: FiniteDuration = FiniteDuration(100, "ms")
    }

    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("Bearer XYZ")))
    implicit val request: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest().withSession(SessionKeys.authToken -> "Bearer XYZ")
    import scala.concurrent.ExecutionContext.Implicits.global

    def withAuthorised[A]: Result =
      await(super.withAuthorised {
        Future.successful(Ok("Hello!"))
      })

    def withAuthorisedAsTrader[A]: Result =
      await(super.withAuthorisedAsTrader { identifier =>
        Future.successful(Ok(identifier))
      })

  }

  "withAuthorised" should {

    "call body when user is authorized" in {
      givenAuditConnector()
      stubForAuthAuthorise(
        "{}",
        "{}"
      )
      val result = TestController.withAuthorised
      status(result) shouldBe 200
      bodyOf(result) shouldBe "Hello!"
    }

    "throw an AutorisationException when user not logged in" in {
      givenUnauthorisedWith("MissingBearerToken")
      an[AuthorisationException] shouldBe thrownBy {
        TestController.withAuthorised
      }
    }
  }

  "withAuthorisedAsTrader" should {

    "call body with arn when valid trader" in {
      givenAuditConnector()
      stubForAuthAuthorise(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-XYZ", "identifiers": [
           |    { "key":"XYZNumber", "value": "fooXyz" }
           |  ]}
           |]}""".stripMargin
      )
      val result = TestController.withAuthorisedAsTrader
      status(result) shouldBe 200
      bodyOf(result) shouldBe "fooXyz"
    }

    "throw an AutorisationException when user not logged in" in {
      givenUnauthorisedWith("MissingBearerToken")
      an[AuthorisationException] shouldBe thrownBy {
        TestController.withAuthorisedAsTrader
      }
    }

    "throw InsufficientEnrolments when trader not enrolled for service" in {
      stubForAuthAuthorise(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-FOO", "identifiers": [
           |    { "key":"XYZNumber", "value": "fooXyz" }
           |  ]}
           |]}""".stripMargin
      )
      an[InsufficientEnrolments] shouldBe thrownBy {
        TestController.withAuthorisedAsTrader
      }
    }

    "throw InsufficientEnrolments when expected trader's identifier missing" in {
      stubForAuthAuthorise(
        "{}",
        s"""{
           |"authorisedEnrolments": [
           |  { "key":"HMRC-XYZ", "identifiers": [
           |    { "key":"BAR", "value": "foo" }
           |  ]}
           |]}""".stripMargin
      )
      an[InsufficientEnrolments] shouldBe thrownBy {
        TestController.withAuthorisedAsTrader
      }
    }
  }

}
