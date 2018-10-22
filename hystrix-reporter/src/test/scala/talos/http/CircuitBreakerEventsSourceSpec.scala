package talos.http

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import talos.http.CircuitBreakerStatsActor.HystrixDashboardEvent

class CircuitBreakerEventsSourceSpec
      extends TestKit(ActorSystem("CircuitBreakerEventsSourceSpec"))
      with FlatSpecLike
      with Matchers
      with BeforeAndAfterAll{

  import scala.concurrent.duration._

  override def afterAll(): Unit = {
    system.terminate()
  }

  "events source" should "periodically send events for streaming data" in {
    val metricsReporterActor = TestProbe("MetricsReporter")

    val receiverEnd = TestProbe("receiver")
    val circuitBreakerEventsSource =
      new CircuitBreakerEventsSource(3 seconds, 10, metricsReporterActor.ref)

    implicit val actorMaterializer = ActorMaterializer()
    circuitBreakerEventsSource.main.runWith(Sink.ignore)

    metricsReporterActor.expectMsg(CircuitBreakerStatsActor.FetchHystrixEvents)
    metricsReporterActor.send(metricsReporterActor.sender(), HystrixDashboardEvent())

  }

}
