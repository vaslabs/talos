package talos.http

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import talos.http.CircuitBreakerStatsActor.StreamTo
class CircuitBreakerStatsActorSpec
      extends TestKit(ActorSystem("HystrixReporterSpec"))
      with WordSpecLike
      with Matchers
      with ImplicitSender
      with BeforeAndAfterAll{

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }


  import Utils.{statsSample => sample}


  "hystrix reporter" can {
    val hystrixReporter = TestActorRef(CircuitBreakerStatsActor.props)
    hystrixReporter ! StreamTo(self)
    "receive stats for circuit breaker" in {
      val statsSample: CircuitBreakerStatsActor.CircuitBreakerStats = sample
      hystrixReporter ! statsSample
      expectMsg(statsSample)
    }
    "receive the latest stats only" in {
      val statsSample = List(sample, sample)
      hystrixReporter ! statsSample(0)
      hystrixReporter ! statsSample(1)
      expectMsg(statsSample(0))
      expectMsg(statsSample(1))
    }

  }

}
