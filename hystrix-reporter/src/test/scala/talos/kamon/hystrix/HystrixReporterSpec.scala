package talos.kamon.hystrix

import java.time.{Clock, Instant, ZoneOffset, ZonedDateTime}

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.eventstream.EventStream
import kamon.{Kamon, MetricReporter}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import talos.events.TalosEvents.model._
import talos.http.CircuitBreakerEventsSource.{CircuitBreakerStats, StreamControl}
import talos.kamon.StatsAggregator

import scala.concurrent.duration._
import scala.util.Try

object HystrixReporterSpec {

  implicit val testClock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)


  def fireSuccessful(times: Int, circuitBreaker: String)(implicit actorSystem: ActorSystem[_]) =
    for (i <- 1 to times) yield actorSystem.eventStream ! EventStream.Publish(SuccessfulCall(circuitBreaker, i seconds))

  def fireFailures(times: Int, circuitBreaker: String)(implicit actorSystem: ActorSystem[_]) =
    for(_ <- 1 to times) yield Try(
      actorSystem.eventStream ! EventStream.Publish(CallFailure(circuitBreaker, 5 seconds))
    )
}

class HystrixReporterSpec extends WordSpec
      with Matchers
      with BeforeAndAfterAll
{

  import HystrixReporterSpec._

  val testKit = ActorTestKit()
  implicit val system = testKit.system

  override def afterAll(): Unit = {
    Kamon.stopAllReporters()
    testKit.shutdownTestKit()
  }


  val statsAggregator: ActorRef[CircuitBreakerEvent] = testKit.spawn(StatsAggregator.behavior(), "statsAggregator")

  val statsGatherer = testKit.createTestProbe[StreamControl]()

  val hystrixReporter: MetricReporter = new HystrixReporter(statsGatherer.ref)
  Kamon.addReporter(hystrixReporter)

  "Hystrix reporter receiving kamon metric snapshots for a single circuit breaker" can {

    val circuitBreaker = "testCircuitBreaker"

    "group successful metrics into one single snapshot event" in {
      fireSuccessful(10, circuitBreaker)
      val statsMessage = statsGatherer.expectMessageType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
          "testCircuitBreaker", 10L, _, false,
          0f, 0L, 0L, 0L, 0L, 0L, 10L, 0L, 0L, 0L,
          latencyExecute_mean, _, _, _, _
        ) if latencyExecute_mean.toSeconds == 5L =>
      }
      statsMessage.currentTime shouldBe ZonedDateTime.now(testClock)
      statsMessage.latencyTotal_mean.toSeconds shouldBe 5L

      statsMessage.latencyExecute.mapValues(_.toSeconds) shouldBe Map(
        "100" -> 9L,
        "90" -> 8L,
        "50" -> 4L,
        "99" -> 9L,
        "0"-> 0L,
        "25" -> 2L,
        "95" -> 9L,
        "75" -> 7L,
        "99.5" -> 9L
      )
    }
    "ignores unrelated metrics" in {
      Kamon.counter("random-counter").increment()
      Try(fireSuccessful(10, circuitBreaker))
      val statsMessage = statsGatherer.expectMessageType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 10L, _, false,
        0f, 0L, 0L, 0L, 0L, 0L, 10L, 0L, 0L, 0L,
        _, _, _, _, _
        ) =>
      }
    }

    "group successful and unsuccessful metrics" in {
      fireSuccessful(8, circuitBreaker)
      fireFailures(2, circuitBreaker)

      val statsMessage = statsGatherer.expectMessageType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 10L, _, false,
        20f, 2L, 2L, 2L, 0L, 0L, 8L, 0L, 0L, 0L,
        _, _, _, _, _
        ) =>
      }
      statsMessage.currentTime shouldBe ZonedDateTime.now(testClock)
      statsMessage.latencyExecute_mean should be > (0 nanos)
      statsMessage.latencyTotal_mean should be > (0 nanos)
      println(statsMessage.latencyExecute)
    }

    "count open circuits" in {
      fireFailures(3, circuitBreaker)
      system.eventStream ! EventStream.Publish(CircuitOpen(circuitBreaker))
      val statsMessage = statsGatherer.expectMessageType[CircuitBreakerStats]
      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 3L, _, true,
        100f, 3L, 3L, 3L, 0L, 0L, 0L, 0L, 0L, 0L,
        _, _, _, _, _
        ) =>
      }
    }

    "count short circuits" in {
      system.eventStream ! EventStream.Publish(ShortCircuitedCall(circuitBreaker))
      val statsMessage = statsGatherer.expectMessageType[CircuitBreakerStats]
      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 1L, _, true,
        100f, 1L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, _, _, _,
        _, _, _
        ) =>
      }
    }

    "count fallback events" in {
      system.eventStream ! EventStream.Publish(CallFailure(circuitBreaker, 3 seconds))
      system.eventStream ! EventStream.Publish(FallbackSuccess(circuitBreaker))
      system.eventStream ! EventStream.Publish(FallbackFailure(circuitBreaker))
      system.eventStream ! EventStream.Publish(FallbackRejected(circuitBreaker))
      val statsMessage = statsGatherer.expectMessageType[CircuitBreakerStats]
      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 1L, _, false,
        100f, 1L, 1L, 1L, 0L, 0L, 0L, 1L, 1L, 1L,_, _,
        _, _, _
        ) =>
      }
    }

  }

  "Hystrix reporter receiving kamon metric snapshots for multiple circuit breakers" can {
    val circuitBreakerFoo = "foo"
    val circuitBreakerBar = "bar"
    "gather stats for all" in {
      fireSuccessful(7, circuitBreakerFoo)
      fireFailures(3, circuitBreakerFoo)
      fireSuccessful(4, circuitBreakerBar)
      fireFailures(1, circuitBreakerBar)
      val first = statsGatherer.expectMessageType[CircuitBreakerStats]
      println(first)
      val second = statsGatherer.expectMessageType[CircuitBreakerStats]
      val barStats = if (first.name == "bar") first else second
      val fooStats = if (second.name == "foo") second else first

      barStats should matchPattern {
        case CircuitBreakerStats(
        "bar", 5L, _, false,
        20f, 1L, 1L, 1L, 0L, 0L, 4L, 0L, 0L, 0L,
        _, _, _, _, _
        ) =>
      }
      fooStats should matchPattern {
        case CircuitBreakerStats(
        "foo", 10L, _, false,
        30f, 3L, 3L, 3L, 0L, 0L, 7L, 0L, 0L, 0L,
        _, _,
        _, _, _
        ) =>
      }
    }
  }
}
