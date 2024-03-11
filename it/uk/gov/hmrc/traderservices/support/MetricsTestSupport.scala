package uk.gov.hmrc.traderservices.support

import com.codahale.metrics.MetricRegistry
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import play.api.Application
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import scala.jdk.CollectionConverters._

trait MetricsTestSupport {
  self: Suite with Matchers =>

  def app: Application

  private var metricsRegistry: MetricRegistry = _

  def givenCleanMetricRegistry(): Unit = {
    val registry = app.injector.instanceOf[Metrics].defaultRegistry
    registry.getMetrics.keySet().asScala.foreach(metric => registry.remove(metric))
    metricsRegistry = registry
  }

  def verifyTimerExistsAndBeenUpdated(metric: String): Unit = {
    val timers = metricsRegistry.getTimers
    val metrics = timers.get(s"Timer-$metric")
    if (metrics == null)
      throw new Exception(s"Metric [$metric] not found, try one of ${timers.keySet()}")
    metrics.getCount should be >= 1L
  }

}
