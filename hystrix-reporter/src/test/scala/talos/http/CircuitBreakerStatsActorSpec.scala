package talos.http

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import talos.http.CircuitBreakerStatsActor.FetchHystrixEvents
class CircuitBreakerStatsActorSpec
      extends TestKit(ActorSystem("HystrixReporterSpec"))
      with WordSpecLike
      with Matchers
      with ImplicitSender
      with BeforeAndAfterAll{

  override def afterAll(): Unit = system.terminate()

  def sample =
    CircuitBreakerStatsActor.CircuitBreakerStats(
      "myCircuitBreaker",
      0L,
      ZonedDateTime.now(),
      false,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      Map.empty,
      0,
      Map.empty
    )


  "hystrix reporter" can {
    val hystrixReporter = TestActorRef(CircuitBreakerStatsActor.props)
    "receive stats for circuit breaker" in {
      val statsSample: CircuitBreakerStatsActor.CircuitBreakerStats = sample
      hystrixReporter ! statsSample
      hystrixReporter ! FetchHystrixEvents
      expectMsg(List(statsSample))
    }
    "receive the latest stats only" in {
      val statsSample = List(sample, sample)
      hystrixReporter ! statsSample(0)
      hystrixReporter ! statsSample(1)
      hystrixReporter ! FetchHystrixEvents
      expectMsg(statsSample)
    }
    "support at most once delivery" in {
      hystrixReporter ! FetchHystrixEvents
      expectMsg(List.empty)
    }

  }

}
