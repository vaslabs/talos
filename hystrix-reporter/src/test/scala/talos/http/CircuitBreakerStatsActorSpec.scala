package talos.http

import akka.actor.ActorSystem
import akka.actor.Status.Success
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

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
    hystrixReporter ! CircuitBreakerEventsSource.Start(self)
    val statsSample: CircuitBreakerStatsActor.CircuitBreakerStats = sample
    val anotherStreamActor = TestProbe()

    "push stats to stream actor" in {
      hystrixReporter !  CircuitBreakerEventsSource.Start(self)
      hystrixReporter ! statsSample
      expectMsg(statsSample)
    }
    "push stats to multiple streams" in {
      hystrixReporter !  CircuitBreakerEventsSource.Start(anotherStreamActor.ref)
      hystrixReporter ! statsSample
      expectMsg(statsSample)
      anotherStreamActor.expectMsg(statsSample)
    }

    "stop streaming to an actor when stream is done" in {
      hystrixReporter ! CircuitBreakerEventsSource.Done(anotherStreamActor.ref)
      anotherStreamActor.expectMsg(Success)
      hystrixReporter ! statsSample
      expectMsg(statsSample)
      anotherStreamActor.expectNoMessage(10 millis)
    }

  }

}
