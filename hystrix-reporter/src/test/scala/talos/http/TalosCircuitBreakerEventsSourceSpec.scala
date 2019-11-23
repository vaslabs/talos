package talos.http

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import kamon.Kamon
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import talos.http.CircuitBreakerEventsSource.StreamControl

class TalosCircuitBreakerEventsSourceSpec
      extends FlatSpec
      with Matchers
      with BeforeAndAfterAll{

  val testKit = ActorTestKit()

  override def afterAll(): Unit = {
    Kamon.stopAllReporters()
    testKit.shutdownTestKit()
  }

  sealed trait IgnoreProtocol
  "events source" should "periodically send events for streaming data" in {
    val metricsReporterActor = testKit.createTestProbe[StreamControl]("MetricsReporter")
    val mockBehaviour: Behavior[IgnoreProtocol] = Behaviors.setup {
      ctx =>
        implicit val actorContext = ctx
        val circuitBreakerEventsSource =
          new CircuitBreakerEventsSource(metricsReporterActor.ref)

        implicit val actorMaterializer = Materializer(ctx)
        circuitBreakerEventsSource.main.runWith(Sink.ignore)
        Behaviors.ignore
    }
    testKit.spawn(mockBehaviour)

    metricsReporterActor.expectMessageType[CircuitBreakerEventsSource.Start]

  }

}
