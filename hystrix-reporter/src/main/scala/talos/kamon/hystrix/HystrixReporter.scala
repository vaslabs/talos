package talos.kamon.hystrix

import java.time.{Clock, ZonedDateTime}

import akka.actor.ActorRef
import com.typesafe.config.Config
import kamon.MetricReporter
import kamon.metric.{MetricDistribution, MetricValue, Percentile, PeriodSnapshot}
import cats.implicits._
import cats.kernel.Semigroup
import talos.http.CircuitBreakerStatsActor.CircuitBreakerStats
import talos.kamon.StatsAggregator

import scala.collection.immutable
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

class HystrixReporter(statsGatherer: ActorRef)(implicit clock: Clock) extends MetricReporter{


  private def findCountersMetricsOfCircuitBreaker(counters: Seq[MetricValue]) =
    counters.foldRight(Map.empty[String, Long])(
      (metricValue, counters) =>
        metricValue.tags.get("eventType").map(
          tagName => Map(tagName -> metricValue.value) |+| counters
        ).getOrElse(Map.empty)
    )

  private implicit val circuitBreakerStatsSemigroup: Semigroup[CircuitBreakerStats] =
    (x: CircuitBreakerStats, y: CircuitBreakerStats) => {
      val fromCounters = if (x.requestCount > 0) x else y
      val fromHistograms = if (x.requestCount > 0) y else x
      CircuitBreakerStats(
        fromCounters.name,
        fromCounters.requestCount,
        fromCounters.currentTime,
        fromCounters.isCircuitBreakerOpen,
        fromCounters.errorPercentage,
        fromCounters.errorCount,
        fromCounters.rollingCountFailure,
        fromCounters.rollingCountExceptionsThrown,
        rollingCountTimeout = fromCounters.rollingCountTimeout,
        fromCounters.rollingCountShortCircuited,
        fromCounters.rollingCountSuccess,
        fromHistograms.latencyExecute_mean,
        fromHistograms.latencyExecute,
        fromHistograms.latencyTotal_mean,
        fromHistograms.latencyTotal
      )
    }

  private implicit val percentileSemigroup: Semigroup[Percentile] =
    (x: Percentile, y: Percentile) => {
      if (x.quantile > y.quantile)
        y
      else if (x.countUnderQuantile > y.quantile)
        x
      else
        y
    }

  private def asCircuitBreakerStats(circuitBreakerName: String, stats: Map[String, Long]): CircuitBreakerStats = {
    import StatsAggregator.Keys
    val successCalls = stats.getOrElse(Keys.Success, 0L)
    val failedCalls = stats.getOrElse(Keys.Failure, 0L)
    val shortCircuited = stats.getOrElse(Keys.ShortCircuit, 0L)
    val timeouts = stats.getOrElse(Keys.Timeout, 0L)
    val allErrors = failedCalls + shortCircuited
    val totalCalls = successCalls + failedCalls + shortCircuited
    val errorPercentage: Float =
      if (totalCalls == 0) 0 else (BigDecimal(allErrors)/totalCalls).floatValue()
    CircuitBreakerStats(
      circuitBreakerName,
      requestCount = totalCalls,
      currentTime = ZonedDateTime.now(clock),
      isCircuitBreakerOpen = shortCircuited > 0 || stats.get(Keys.Open).map(_ > 0).getOrElse(false),
      errorPercentage = errorPercentage,
      errorCount = allErrors,
      failedCalls + timeouts,
      failedCalls,
      rollingCountTimeout = timeouts,
      shortCircuited,
      successCalls,
      0 millis,
      Map.empty,
      0 millis,
      Map.empty
    )
  }


  private def findCountersMetrics(counters: Seq[MetricValue]): Map[String, CircuitBreakerStats] =
    counters.groupBy(_.name.substring(StatsAggregator.Keys.CounterPrefix.length()))
      .mapValues(findCountersMetricsOfCircuitBreaker(_))
    .map {
      case (name, stats) => name -> asCircuitBreakerStats(name, stats)
    }

  def normalize(quantile: Double, percentile: Percentile): Percentile = {
    if (quantile != percentile.quantile)
      new Percentile {
        override val quantile: Double = quantile

        override val value: Long = percentile.value

        override val countUnderQuantile: Long = percentile.countUnderQuantile
      }
    else
      percentile
  }

  def findHistogramMetricsOfCircuitBreaker(name: String, distributions: Seq[MetricDistribution]): CircuitBreakerStats = {
    def mean(t: (Long, Long)) = if (t._2 == 0) 0L else (t._1 / t._2)
    val latencyExecuteMean: Long =
      mean(distributions.foldLeft((0L, 0L)) {
        (stats, md) => stats |+| (md.distribution.sum, md.distribution.count)
      })
    def percentile(p: Double)(seq: Seq[Long]): Long = {
      val sorted = seq.sorted
      math.ceil((seq.length - 1) * (p/100.0)).toLong
    }

    val allPercentiles: Seq[String] = Seq(
      "0", "25", "50", "75",
      "90", "95", "99", "99.5", "100"
    )

    val latencyPercentiles: Seq[Map[String, Percentile]] = distributions.map(
      md => for {
        percentile <- allPercentiles
        p = percentile.toDouble
        hystrixPercentile = normalize(p, md.distribution.percentile(p))
      } yield (percentile -> hystrixPercentile)
    ).map(seq => Map(seq: _*))

    val latencyExecute: Map[String, FiniteDuration] =
      latencyPercentiles.foldLeft(Map.empty[String, Percentile])(_ |+| _)
        .map {
          case (name, percentile) => name.toString -> (percentile.value nanos)
      }

    import scala.concurrent.duration._

    CircuitBreakerStats(
      name,
      0L,
      ZonedDateTime.now(clock),
      false, 0, 0,0,0,0,0,0,
      latencyExecuteMean nanos,
      latencyExecute,
      latencyExecuteMean nanos,
      latencyExecute
    )
  }

  private def findHistogramMetrics(histograms: Seq[MetricDistribution]): Map[String, CircuitBreakerStats] = {
    histograms.groupBy(_.name.substring(StatsAggregator.Keys.HistrogramPrefix.length)).map {
      case (name, metrics) => name -> findHistogramMetricsOfCircuitBreaker(name, metrics)
    }
  }

  def merge(countersMetrics: immutable.Iterable[CircuitBreakerStats], histogramMetrics: immutable.Iterable[CircuitBreakerStats]): Unit = ???

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val outcome = Try {
      val countersMetrics = findCountersMetrics(snapshot.metrics.counters)
      val histogramMetrics = findHistogramMetrics(snapshot.metrics.histograms)
      val mergedStats = countersMetrics |+| histogramMetrics
      mergedStats.foreach {
        case (circuitBreaker, circuitBreakerStats) =>
          if (circuitBreakerStats.requestCount > 0)
            statsGatherer ! circuitBreakerStats
      }
    }
    outcome match {
      case Success(_) => ()
      case Failure(t) => t.printStackTrace()
    }
  }

  override def start(): Unit = ()

  override def stop(): Unit = ()

  override def reconfigure(config: Config): Unit = ()
}

