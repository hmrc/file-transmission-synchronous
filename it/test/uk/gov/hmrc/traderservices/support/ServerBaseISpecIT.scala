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

package uk.gov.hmrc.traderservices.support

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.traderservices.wiring.AppConfig

import javax.inject.Inject
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.traderservices.wiring.AppConfigImpl

abstract class ServerBaseISpecIT extends BaseISpecIT with GuiceOneServerPerSuite with TestApplication with ScalaFutures {

  override implicit lazy val app: Application = defaultAppBuilder
    .bindings(
      bind(classOf[PortNumberProvider]).toInstance(new PortNumberProvider(port)),
      bind(classOf[AppConfig]).to(classOf[TestAppConfig])
    )
    .build()

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(4, Seconds), interval = Span(1, Seconds))

}

class PortNumberProvider(port: => Int) {
  def value: Int = port
}

class TestAppConfig @Inject() (config: ServicesConfig, port: PortNumberProvider) extends AppConfigImpl(config)
