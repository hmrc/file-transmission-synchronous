package uk.gov.hmrc.traderservices.support

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.traderservices.wiring.AppConfig
import javax.inject.Inject
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.traderservices.wiring.AppConfigImpl

abstract class ServerBaseISpec extends BaseISpec with OneServerPerSuite with TestApplication with ScalaFutures {

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
