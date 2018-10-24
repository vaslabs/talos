package talos.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.duration._
class CircuitBreakerEventsSource
      (hystrixReporter: ActorRef)
      (implicit actorSystem: ActorSystem){

  import CircuitBreakerEventsSource._
  import io.circe.syntax._
  import json_adapters._


  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  def main: Source[ServerSentEvent, _] = {
    val prematerialisedSource =
      Source.actorRef[CircuitBreakerStatsActor.CircuitBreakerStats](
        1000, OverflowStrategy.dropTail
      ).preMaterialize()

    val streamingActor = prematerialisedSource._1

    hystrixReporter ! CircuitBreakerEventsSource.Start(streamingActor)


    prematerialisedSource._2.map(_.asJson.noSpaces)
      .map(ServerSentEvent(_))
      .keepAlive(2 second, () => ServerSentEvent.heartbeat)
      .watchTermination(){(_, done) =>
        done.map(_ => hystrixReporter ! Done(streamingActor))(actorSystem.dispatcher)
      }

  }
}

object CircuitBreakerEventsSource {
  sealed trait StreamControl
  case class Done(actorRef: ActorRef) extends StreamControl
  case class Start(actorRef: ActorRef) extends StreamControl
}