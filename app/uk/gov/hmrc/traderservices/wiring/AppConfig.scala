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

package uk.gov.hmrc.traderservices.wiring

import com.google.inject.ImplementedBy
import javax.inject.Inject
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.duration.FiniteDuration

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {

  val appName: String

  val authBaseUrl: String

  val authorisedServiceName: String

  val authorisedIdentifierKey: String

  val eisBaseUrl: String

  val eisFileTransferHost: String

  val eisFileTransferPort: Int

  val eisFileTransferApiPath: String

  val eisAuthorizationToken: String

  val eisEnvironment: String

  val unitInterval: FiniteDuration
}

class AppConfigImpl @Inject() (config: ServicesConfig) extends AppConfig {

  override val appName: String = config.getString("appName")

  override val authBaseUrl: String = config.baseUrl("auth")

  override val authorisedServiceName: String = config.getString("authorisedServiceName")

  override val authorisedIdentifierKey: String = config.getString("authorisedIdentifierKey")

  override val eisBaseUrl: String = config.baseUrl("eis.cpr.filetransfer.caseevidence")

  override val eisFileTransferHost: String = config.getConfString(
    "eis.cpr.filetransfer.caseevidence.host",
    throw new IllegalStateException(
      "Missing [microservice.services.eis.cpr.filetransfer.caseevidence.host] configuration property"
    )
  )

  override val eisFileTransferPort: Int = config.getConfInt(
    "eis.cpr.filetransfer.caseevidence.port",
    throw new IllegalStateException(
      "Missing [microservice.services.eis.cpr.filetransfer.caseevidence.port] configuration property"
    )
  )

  override val eisFileTransferApiPath: String =
    config.getConfString(
      "eis.cpr.filetransfer.caseevidence.path",
      throw new IllegalStateException(
        "Missing [microservice.services.eis.cpr.filetransfer.caseevidence.path] configuration property"
      )
    )

  override val eisAuthorizationToken: String =
    config.getConfString(
      "eis.cpr.filetransfer.caseevidence.token",
      throw new IllegalStateException(
        "Missing [microservice.services.eis.cpr.filetransfer.caseevidence.token] configuration property"
      )
    )

  override val eisEnvironment: String =
    config.getConfString(
      "eis.cpr.filetransfer.caseevidence.environment",
      throw new IllegalStateException(
        "Missing [microservice.services.eis.cpr.filetransfer.caseevidence.environment] configuration property"
      )
    )

  override val unitInterval: FiniteDuration =
    FiniteDuration(config.getInt("unit-interval-milliseconds"), "ms")

}
