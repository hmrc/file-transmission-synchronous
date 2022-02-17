package uk.gov.hmrc.traderservices.support

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import uk.gov.hmrc.traderservices.stubs.AuthStubs

abstract class AppBaseISpec extends BaseISpec with GuiceOneAppPerSuite with TestApplication with AuthStubs {

  override implicit lazy val app: Application = defaultAppBuilder.build()

}
