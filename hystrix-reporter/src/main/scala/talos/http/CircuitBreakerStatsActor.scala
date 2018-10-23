package talos.http

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorRef, Props, Status}

import scala.concurrent.duration.FiniteDuration


object CircuitBreakerStatsActor {
  def props: Props = Props(new CircuitBreakerStatsActor)


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
      latencyExecute_mean: FiniteDuration,
      latencyExecute: Map[String, FiniteDuration],
      latencyTotal_mean: FiniteDuration,
      latencyTotal: Map[String, FiniteDuration],
      reportingHosts: Int = 1
    )

  case class StreamTo(reportTo: ActorRef)

}

class CircuitBreakerStatsActor extends Actor {

  import talos.http.CircuitBreakerStatsActor._

  override def postStop(): Unit = {
    println("Circtuit breaker stats actor was stopped")
  }
  override def receive: Receive = {
    case StreamTo(actorRef) =>
      context.become(sendEventsTo(actorRef))
  }

  private[this] def sendEventsTo(actorRef: ActorRef): Receive = {
    case StreamTo(replacement) =>
      actorRef ! Status.Success
      context.become(sendEventsTo(replacement))
    case cbs: CircuitBreakerStats =>
      actorRef ! cbs
  }
}
