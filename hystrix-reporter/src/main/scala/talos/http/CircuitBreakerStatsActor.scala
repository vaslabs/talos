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

}

class CircuitBreakerStatsActor extends Actor {

  import talos.http.CircuitBreakerStatsActor._

  override def postStop(): Unit = {
    println("Circtuit breaker stats actor was stopped")
  }
  override def receive: Receive = sendEventsTo(Set.empty)

  private[this] def sendEventsTo(streamTo: Set[ActorRef]): Receive = {
    case CircuitBreakerEventsSource.Start(streamingActor) =>
      context.become(sendEventsTo(streamTo + streamingActor))
    case cbs: CircuitBreakerStats =>
      streamTo.foreach(_ ! cbs)
    case CircuitBreakerEventsSource.Done(actorRef) =>
      val newSet = streamTo - actorRef
      actorRef ! Status.Success
      context.become(sendEventsTo(newSet))
  }
}
