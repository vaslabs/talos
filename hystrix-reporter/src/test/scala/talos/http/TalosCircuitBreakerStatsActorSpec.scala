package talos.http


import akka.actor.testkit.typed.scaladsl.ActorTestKit
import kamon.Kamon
import org.scalatest.BeforeAndAfterAll
import talos.http.CircuitBreakerEventsSource.{CircuitBreakerStats, ExposedEvent, StreamEnded}

import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TalosCircuitBreakerStatsActorSpec
      extends AnyWordSpec
      with Matchers
      with BeforeAndAfterAll{

  val testKit = ActorTestKit()
  val eventListener = testKit.createTestProbe[ExposedEvent]()

  override def afterAll(): Unit = {
    Kamon.stopAllReporters()
    testKit.shutdownTestKit()
  }

  import Utils.{statsSample => sample}


  "hystrix reporter" can {
    val hystrixReporter = testKit.spawn(CircuitBreakerStatsActor.behaviour)
    hystrixReporter ! CircuitBreakerEventsSource.Start(eventListener.ref)
    val statsSample: CircuitBreakerStats = sample
    val anotherStreamActor = testKit.createTestProbe[ExposedEvent]()

    "push stats to stream actor" in {
      hystrixReporter !  CircuitBreakerEventsSource.Start(eventListener.ref)
      hystrixReporter ! statsSample
      eventListener.expectMessage(statsSample)
    }
    "push stats to multiple streams" in {
      hystrixReporter !  CircuitBreakerEventsSource.Start(anotherStreamActor.ref)
      hystrixReporter ! statsSample
      eventListener.expectMessage(statsSample)
      anotherStreamActor.expectMessage(statsSample)
    }

    "stop streaming to an actor when stream is done" in {
      hystrixReporter ! CircuitBreakerEventsSource.Done(anotherStreamActor.ref)
      anotherStreamActor.expectMessage(StreamEnded)
      hystrixReporter ! statsSample
      eventListener.expectMessage(statsSample)
      anotherStreamActor.expectNoMessage(10 millis)
    }

  }

}
