package talos.kamon.hystrix

import java.time.{Clock, ZonedDateTime}

import akka.actor.ActorRef
import com.typesafe.config.Config
import kamon.MetricReporter
import kamon.metric.{MetricValue, PeriodSnapshot}
import cats.implicits._
import talos.http.CircuitBreakerStatsActor.CircuitBreakerStats

class HystrixReporter(statsGatherer: ActorRef)(implicit clock: Clock) extends MetricReporter{


  private def findMetricsOfCircuitBreaker(counters: Seq[MetricValue]) =
    counters.foldRight(Map.empty[String, Long])(
      (metricValue, counters) =>
        metricValue.tags.get("eventType").map(
          tagName => Map(tagName -> metricValue.value) |+| counters
        ).getOrElse(Map.empty)
    )


  private def asCircuitBreakerStats(circuitBreakerName: String, stats: Map[String, Long]): CircuitBreakerStats = {
    val successCalls = stats.getOrElse("successful-call", 0L)
    val failedCalls = stats.getOrElse("failed-call", 0L)
    val shortCircuited = stats.getOrElse("short-circuited", 0L)
    val timeouts = stats.getOrElse("call-timeout", 0L)
    val allErrors = failedCalls + shortCircuited
    val totalCalls = successCalls + failedCalls + shortCircuited
    val errorPercentage: Float =
      if (totalCalls == 0) 0 else (BigDecimal(allErrors)/totalCalls).floatValue()
    CircuitBreakerStats(
      circuitBreakerName,
      requestCount = totalCalls,
      currentTime = ZonedDateTime.now(clock),
      isCircuitBreakerOpen = stats.get("circuit-open").map(_ > 0).getOrElse(false),
      errorPercentage = errorPercentage,
      errorCount = allErrors,
      failedCalls + timeouts,
      failedCalls,
      shortCircuited,
      successCalls,
      0,
      Map.empty,
      Map.empty,
      Map.empty
    )
  }


  private def findMetrics(counters: Seq[MetricValue]) =
    counters.groupBy(_.name).mapValues(findMetricsOfCircuitBreaker(_))
    .map {
      case (name, stats) => asCircuitBreakerStats(name, stats)
    }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    findMetrics(snapshot.metrics.counters).map(statsGatherer ! _)
  }

  override def start(): Unit = ()

  override def stop(): Unit = ()

  override def reconfigure(config: Config): Unit = ()
}
