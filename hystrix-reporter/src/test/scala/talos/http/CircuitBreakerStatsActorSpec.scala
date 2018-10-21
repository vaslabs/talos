package talos.http

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import cats.data.NonEmptyList
import org.scalatest.{Matchers, WordSpecLike}
import talos.http.CircuitBreakerStatsActor.FetchHystrixEvents

import scala.concurrent.duration._
class CircuitBreakerStatsActorSpec
      extends TestKit(ActorSystem("HystrixReporterSpec"))
      with WordSpecLike
      with Matchers
      with ImplicitSender {

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
      Map.empty,
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

  }

}
