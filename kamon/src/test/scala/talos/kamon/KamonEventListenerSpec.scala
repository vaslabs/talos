package talos.kamon

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.pattern.CircuitBreaker
import com.typesafe.config.Config
import kamon.metric.{MetricValue, PeriodSnapshot}
import kamon.{Kamon, MetricReporter}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.duration._
import scala.util.Try
class KamonEventListenerSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  var gatheringValues = Map.empty[String, Long]

  val testKit = ActorTestKit()

  override def afterAll() = {
    testKit.shutdownTestKit()
  }

  Kamon.addReporter(new MetricReporter {
    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      def name(metricValue: MetricValue): String =
        s"${metricValue.name}-${metricValue.tags.get("eventType").get}"
      snapshot.metrics.counters.foreach {
        mv =>
          gatheringValues += (name(mv) -> gatheringValues.get(name(mv)).map(_ + mv.value).getOrElse(mv.value))
      }
    }

    override def start(): Unit = ()

    override def stop(): Unit = ()

    override def reconfigure(config: Config): Unit = ()
  })

  "circuit breaker" should "integrate with kamon" in {

    import talos.events.syntax._


    val circuitBreakerName = "myCircuitBreaker"

    testKit.spawn(StatsAggregator.behavior()).toUntyped

    implicit val untypedActorSystem = testKit.system.toUntyped

    val circuitBreakerWithEventStreamReporting =
      CircuitBreaker.withEventReporting(
        circuitBreakerName,
        testKit.scheduler,
        maxFailures = 5,
        callTimeout = 1 second,
        resetTimeout = 5 seconds
      )

    for (i <- 1 to 10) yield circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => i)

    for (_ <- 1 to 10) yield Try(
      circuitBreakerWithEventStreamReporting.callWithSyncCircuitBreaker(() => throw new RuntimeException)
    )


    Thread.sleep(2000)

    gatheringValues shouldBe Map(
      "circuit-breaker-myCircuitBreaker-success-call" -> 10,
      "circuit-breaker-myCircuitBreaker-failed-call" -> 5,
      "circuit-breaker-myCircuitBreaker-short-circuited" -> 5,
      "circuit-breaker-myCircuitBreaker-circuit-open" -> 1
    )
  }

}
