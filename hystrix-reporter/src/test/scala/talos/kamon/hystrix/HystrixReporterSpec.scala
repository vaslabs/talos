package talos.kamon.hystrix

import java.time.{Clock, Instant, ZoneOffset, ZonedDateTime}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.CircuitBreaker
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import kamon.{Kamon, MetricReporter}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import talos.events.TalosEvents.model.CircuitBreakerEvent
import talos.http.CircuitBreakerStatsActor.CircuitBreakerStats
import talos.kamon.StatsAggregator

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object HystrixReporterSpec {
  import talos.events.syntax._

  implicit val testClock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  def createCircuitBreaker(name: String = "testCircuitBreaker")
                          (implicit actorSystem: ActorSystem) =
    CircuitBreaker.withEventReporting(
      "testCircuitBreaker",
      actorSystem.scheduler,
      5,
      2 seconds,
      5 seconds
    )

  def fireSuccessful(times: Int, circuitBreaker: CircuitBreaker): Seq[Int] =
    for (i <- 1 to times) yield circuitBreaker.callWithSyncCircuitBreaker(() => i)

  def fireFailures(times: Int, circuitBreaker: CircuitBreaker) =
    for(i <- 1 to times) yield Try(
      circuitBreaker.callWithSyncCircuitBreaker(() => throw new RuntimeException())
    )
}

class HystrixReporterSpec
      extends TestKit(ActorSystem("HystrixReporterSpec"))
      with Matchers
      with WordSpecLike
      with BeforeAndAfterAll
      with ImplicitSender
{

  import HystrixReporterSpec._

  override def afterAll(): Unit = {
    system.terminate()
    Kamon.stopAllReporters()
    system.eventStream.unsubscribe(statsAggregator)
  }


  val statsAggregator: ActorRef = {
    import akka.actor.typed.scaladsl.adapter._
    implicit val timeout: Timeout = Timeout(2 seconds)
    val typedActor =
      Await.result(system.toTyped.systemActorOf[CircuitBreakerEvent](StatsAggregator.behavior(), "statsAggregator"), 2 seconds)
    typedActor.toUntyped
  }
  system.eventStream.subscribe(statsAggregator, classOf[CircuitBreakerEvent])

  "Hystrix reporter receiving kamon metric snapshots for a single circuit breaker" can {

    val circuitBreaker = createCircuitBreaker()
    val statsGatherer: TestProbe = TestProbe()

    val hystrixReporter: MetricReporter = new HystrixReporter(statsGatherer.ref)
    Kamon.addReporter(hystrixReporter)

    "group successful metrics into one single snapshot event" in {
      val results: Seq[Int] = fireSuccessful(10, circuitBreaker)
      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
          "testCircuitBreaker", 10L, time, false,
          0f, 0L, 0L, 0L, 0L, 10L, latencyExecute_mean, latencyExecute,
          latencyTotal_mean, latencyTotal, 1
        ) =>
      }
      statsMessage.currentTime shouldBe ZonedDateTime.now(testClock)
      statsMessage.latencyExecute_mean should be > 0L
      statsMessage.latencyTotal_mean should be > 0L
    }

    "group successful and unsuccessful metrics" in {
      val results: Seq[Int] = fireSuccessful(8, circuitBreaker)
      val failures = fireFailures(2, circuitBreaker)

      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 10L, time, false,
        0.2f, 2L, 2L, 2L, 0L, 8L, latencyExecute_mean, latencyExecute,
        latencyTotal_mean, latencyTotal, 1
        ) =>
      }
      statsMessage.currentTime shouldBe ZonedDateTime.now(testClock)
      statsMessage.latencyExecute_mean should be > 0L
      statsMessage.latencyTotal_mean should be > 0L
    }
    "count open circuits" in {
      val failures = fireFailures(3, circuitBreaker)
      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]
      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 3L, time, true,
        1f, 3L, 3L, 3L, 0L, 0L, latencyExecute_mean, latencyExecute,
        latencyTotal_mean, latencyTotal, 1
        ) =>
      }
    }

    "count short circuits" in {
      Try(fireFailures(1, circuitBreaker))
      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]
      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 1L, time, true,
        1f, 1L, 0L, 0L, 1L, 0L, latencyExecute_mean, latencyExecute,
        latencyTotal_mean, latencyTotal, 1
        ) =>
      }
    }



  }

}
