package talos.http

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CircuitBreakerEventsSourceSpec
      extends TestKit(ActorSystem("CircuitBreakerEventsSourceSpec"))
      with FlatSpecLike
      with Matchers
      with BeforeAndAfterAll{

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "events source" should "periodically send events for streaming data" in {
    val metricsReporterActor = TestProbe("MetricsReporter")

    val circuitBreakerEventsSource =
      new CircuitBreakerEventsSource(metricsReporterActor.ref)

    implicit val actorMaterializer = ActorMaterializer()
    circuitBreakerEventsSource.main.runWith(Sink.ignore)

    metricsReporterActor.expectMsgType[CircuitBreakerStatsActor.StreamTo]

  }

}
