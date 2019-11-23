package talos.http


import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import talos.http.CircuitBreakerEventsSource.{CircuitBreakerStats, ExposedEvent, StreamEnded}

object CircuitBreakerStatsActor {

  def behaviour: Behavior[CircuitBreakerEventsSource.StreamControl] =
    withSubscribers(Set.empty)


  private def withSubscribers(streamTo: Set[ActorRef[ExposedEvent]]): Behavior[CircuitBreakerEventsSource.StreamControl] =
    Behaviors.receiveMessage{
      case CircuitBreakerEventsSource.Start(streamingActor) =>
        withSubscribers(streamTo + streamingActor)
      case cbs: CircuitBreakerStats =>
        streamTo.foreach(_ ! cbs)
        Behaviors.same
      case CircuitBreakerEventsSource.Done(actorRef) =>
        val newSet = streamTo - actorRef
        actorRef ! StreamEnded
        withSubscribers(newSet)
  }

}
