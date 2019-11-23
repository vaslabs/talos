package talos.kamon

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.Config
import kamon.metric.{MetricValue, PeriodSnapshot}
import kamon.{Kamon, MetricReporter}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import talos.events.TalosEvents.model._

import scala.concurrent.duration._
class KamonEventListenerSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  var gatheringValues = Map.empty[String, Long]

  val testKit = ActorTestKit()

  sealed trait Ignore

  override def afterAll() = {
    Kamon.stopAllReporters()
    testKit.shutdownTestKit()
  }

  def guardian: Behavior[Ignore] = Behaviors.setup[Ignore] {
    ctx =>
      implicit val actorContext = ctx
      val stopKamon = StatsAggregator.kamon()

      Behaviors.receive[Ignore] {
        case _ => Behaviors.ignore
      }.receiveSignal {
        case (_, PostStop) =>
          stopKamon.unsafeRunSync()
          Behaviors.stopped
      }
  }

  override def beforeAll(): Unit = {
    testKit.spawn(guardian, "KamonEventGuardian")
    super.beforeAll()
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

    val circuitBreakerName = "myCircuitBreaker"
    Thread.sleep(1500)

    for (i <- 1 to 10) yield testKit.system.eventStream ! EventStream.Publish(SuccessfulCall(circuitBreakerName, i seconds))

    for (_ <- 1 to 5) yield testKit.system.eventStream ! EventStream.Publish(CallFailure(circuitBreakerName, 5 seconds))
    testKit.system.eventStream ! EventStream.Publish(CircuitOpen(circuitBreakerName))

    for (_ <- 1 to 5) yield testKit.system.eventStream ! EventStream.Publish(ShortCircuitedCall(circuitBreakerName))

    testKit.system.eventStream ! EventStream.Publish(FallbackSuccess(circuitBreakerName))
    testKit.system.eventStream ! EventStream.Publish(FallbackFailure(circuitBreakerName))
    testKit.system.eventStream ! EventStream.Publish(FallbackRejected(circuitBreakerName))

    Thread.sleep(1500)

    gatheringValues shouldBe Map(
      "circuit-breaker-myCircuitBreaker-success-call" -> 10,
      "circuit-breaker-myCircuitBreaker-failed-call" -> 5,
      "circuit-breaker-myCircuitBreaker-short-circuited" -> 5,
      "circuit-breaker-myCircuitBreaker-circuit-open" -> 1,
      "circuit-breaker-myCircuitBreaker-fallback-success" -> 1,
      "circuit-breaker-myCircuitBreaker-fallback-failure" -> 1,
      "circuit-breaker-myCircuitBreaker-fallback-rejected" -> 1
    )


  }

}
