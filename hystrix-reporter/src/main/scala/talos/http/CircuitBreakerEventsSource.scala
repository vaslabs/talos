package talos.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.duration._
class CircuitBreakerEventsSource
      (hystrixReporter: ActorRef)
      (implicit actorSystem: ActorSystem){

  import io.circe.syntax._
  import json_adapters._



  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  def main: Source[ServerSentEvent, _] = {
    val prematerialisedSource =
      Source.actorRef[CircuitBreakerStatsActor.CircuitBreakerStats](
        1000, OverflowStrategy.dropTail
      ).preMaterialize()

    val reportTo = prematerialisedSource._1

    hystrixReporter ! CircuitBreakerStatsActor.StreamTo(reportTo)

    prematerialisedSource._2.map(_.asJson.noSpaces)
      .map(ServerSentEvent(_))
      .keepAlive(2 second, () => ServerSentEvent.heartbeat)


  }
}
