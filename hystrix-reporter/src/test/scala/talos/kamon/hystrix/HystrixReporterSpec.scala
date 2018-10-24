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
      name,
      actorSystem.scheduler,
      5,
      2 seconds,
      5 seconds
    )

  def fireSuccessful(times: Int, circuitBreaker: CircuitBreaker): Seq[Int] =
    for (i <- 1 to times) yield circuitBreaker.callWithSyncCircuitBreaker(() => i)

  def fireFailures(times: Int, circuitBreaker: CircuitBreaker) =
    for(_ <- 1 to times) yield Try(
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
    ()
  }


  val statsAggregator: ActorRef = {
    import akka.actor.typed.scaladsl.adapter._
    implicit val timeout: Timeout = Timeout(2 seconds)
    val typedActor =
      Await.result(system.toTyped.systemActorOf[CircuitBreakerEvent](StatsAggregator.behavior(), "statsAggregator"), 2 seconds)
    typedActor.toUntyped
  }

  val statsGatherer: TestProbe = TestProbe()

  val hystrixReporter: MetricReporter = new HystrixReporter(statsGatherer.ref)
  Kamon.addReporter(hystrixReporter)

  "Hystrix reporter receiving kamon metric snapshots for a single circuit breaker" can {

    val circuitBreaker = createCircuitBreaker()

    "group successful metrics into one single snapshot event" in {
      fireSuccessful(10, circuitBreaker)
      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
          "testCircuitBreaker", 10L, _, false,
          0f, 0L, 0L, 0L, 0L, 0L, 10L,
          _, _, _, _, _
        ) =>
      }
      statsMessage.currentTime shouldBe ZonedDateTime.now(testClock)
      statsMessage.latencyExecute_mean should be > (0 nanos)
      statsMessage.latencyTotal_mean should be > (0 nanos)
      println(statsMessage.latencyExecute)
      statsMessage.latencyExecute.get("100").get should be > statsMessage.latencyTotal_mean
    }
    "ignores unrelated metrics" in {
      Kamon.counter("random-counter").increment()
      Try(fireSuccessful(10, circuitBreaker))
      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 10L, _, false,
        0f, 0L, 0L, 0L, 0L, 0L, 10L,
        _, _, _, _, _
        ) =>
      }
    }

    "group successful and unsuccessful metrics" in {
      fireSuccessful(8, circuitBreaker)
      fireFailures(2, circuitBreaker)

      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]

      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 10L, _, false,
        20f, 2L, 2L, 2L, 0L, 0L, 8L,
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
      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]
      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 3L, _, true,
        100f, 3L, 3L, 3L, 0L, 0L, 0L,
        _, _, _, _, _
        ) =>
      }
    }

    "count short circuits" in {
      Try(fireFailures(1, circuitBreaker))
      val statsMessage = statsGatherer.expectMsgType[CircuitBreakerStats]
      statsMessage should matchPattern {
        case CircuitBreakerStats(
        "testCircuitBreaker", 1L, _, true,
        100f, 1L, 0L, 0L, 0L, 1L, 0L, _, _,
        _, _, _
        ) =>
      }
    }

  }

  "Hystrix reporter receiving kamon metric snapshots for a multiple circuit breaker" can {
    val circuitBreakerFoo = createCircuitBreaker("foo")
    val circuitBreakerBar = createCircuitBreaker("bar")
    "gather stats for all" in {
      fireSuccessful(7, circuitBreakerFoo)
      fireFailures(3, circuitBreakerFoo)
      fireSuccessful(4, circuitBreakerBar)
      fireFailures(1, circuitBreakerBar)
      val first = statsGatherer.expectMsgType[CircuitBreakerStats]
      println(first)
      val second = statsGatherer.expectMsgType[CircuitBreakerStats]
      val barStats = if (first.name == "bar") first else second
      val fooStats = if (second.name == "foo") second else first

      barStats should matchPattern {
        case CircuitBreakerStats(
        "bar", 5L, _, false,
        20f, 1L, 1L, 1L, 0L, 0L, 4L,
        _, _, _, _, _
        ) =>
      }
      fooStats should matchPattern {
        case CircuitBreakerStats(
        "foo", 10L, _, false,
        30f, 3L, 3L, 3L, 0L, 0L, 7L,
        _, _,
        _, _, _
        ) =>
      }
    }
  }
}
