package talos.http

import java.time.ZonedDateTime

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{Materializer, OverflowStrategy}
import talos.http.CircuitBreakerEventsSource.StreamControl

import scala.concurrent.duration._
import io.circe.syntax._
import json_adapters._
class CircuitBreakerEventsSource
      (hystrixReporter: ActorRef[StreamControl])
      (implicit ctx: ActorContext[_]){

  import CircuitBreakerEventsSource._


  implicit val materializer: Materializer = Materializer(ctx)

  def main: Source[ServerSentEvent, _] = {
    val prematerialisedSource =
      ActorSource.actorRef[CircuitBreakerEventsSource.ExposedEvent]({
        case StreamEnded => ()
        }, {
        case StreamFailed(t) => t
        },
        1000,
        OverflowStrategy.dropTail
      )

    val (streamingActor, source) = prematerialisedSource.preMaterialize()

    hystrixReporter ! CircuitBreakerEventsSource.Start(streamingActor)


    source.map(_.asJson.noSpaces)
      .map(ServerSentEvent(_))
      .keepAlive(2 second, () => ServerSentEvent.heartbeat)
      .watchTermination(){(_, done) =>
        done.map(_ => hystrixReporter ! Done(streamingActor))(ctx.executionContext)
      }

  }
}

object CircuitBreakerEventsSource {
  sealed trait StreamControl
  case class Done(actorRef: ActorRef[ExposedEvent]) extends StreamControl
  case class Start(actorRef: ActorRef[ExposedEvent]) extends StreamControl

  sealed trait StatsEvent extends StreamControl
  sealed trait ExposedEvent
  case class CircuitBreakerStats
  (
    name: String,
    requestCount: Long,
    currentTime: ZonedDateTime,
    isCircuitBreakerOpen: Boolean,
    errorPercentage: Float,
    errorCount: Long,
    rollingCountFailure: Long,
    rollingCountExceptionsThrown: Long,
    rollingCountTimeout: Long,
    rollingCountShortCircuited: Long,
    rollingCountSuccess: Long,
    rollingCountFallbackSuccess: Long,
    rollingCountFallbackFailure: Long,
    rollingCountFallbackRejected: Long,
    latencyExecute_mean: FiniteDuration,
    latencyExecute: Map[String, FiniteDuration],
    latencyTotal_mean: FiniteDuration,
    latencyTotal: Map[String, FiniteDuration],
    propertyValue_metricsRollingStatisticalWindowInMilliseconds: FiniteDuration
  ) extends StatsEvent with ExposedEvent

  case object StreamEnded extends ExposedEvent
  case class StreamFailed(error: Throwable) extends ExposedEvent
}