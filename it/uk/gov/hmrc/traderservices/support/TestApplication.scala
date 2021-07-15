package uk.gov.hmrc.traderservices.support

import play.api.inject.guice.GuiceApplicationBuilder

trait TestApplication {
  _: BaseISpec =>

  def defaultAppBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"                                     -> wireMockPort,
        "microservice.services.eis.cpr.filetransfer.caseevidence.host"        -> wireMockHost,
        "microservice.services.eis.cpr.filetransfer.caseevidence.port"        -> wireMockPort,
        "microservice.services.eis.cpr.filetransfer.caseevidence.token"       -> "dummy-it-token",
        "microservice.services.eis.cpr.filetransfer.caseevidence.environment" -> "it",
        "metrics.enabled"                                                     -> true,
        "auditing.enabled"                                                    -> true,
        "auditing.consumer.baseUri.host"                                      -> wireMockHost,
        "auditing.consumer.baseUri.port"                                      -> wireMockPort,
        "unit-interval-milliseconds"                                          -> 1
      )
}
