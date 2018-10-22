package talos.events

import akka.actor.ActorSystem
import akka.event.EventStream
import akka.pattern.CircuitBreaker

import scala.concurrent.duration._

object TalosEvents {
  import model._

  def wrap(circuitBreaker: CircuitBreaker, identifier: String)(implicit actorSystem: ActorSystem): CircuitBreaker = {
    def publish(event: EventStream#Event) = actorSystem.eventStream.publish(event)
    circuitBreaker.addOnCallSuccessListener(
      elapsedTime => publish(SuccessfulCall(identifier, elapsedTime nanoseconds))
    ).addOnCallFailureListener(
      elapsedTime => publish(CallFailure(identifier, elapsedTime nanoseconds))
    ).addOnCallTimeoutListener(
      elapsedTime => publish(CallTimeout(identifier, elapsedTime nanoseconds))
    ).addOnOpenListener(
      () => publish(CircuitOpen(identifier))
    ).addOnHalfOpenListener(
      () => publish(CircuitHalfOpen(identifier))
    ).addOnCloseListener(
      () => publish(CircuitClosed(identifier))
    ).addOnCallBreakerOpenListener(
      () => publish(ShortCircuitedCall(identifier))
    )
  }

  object model {

    sealed trait CircuitBreakerEvent {
      val circuitBreakerName: String
    }

    case class SuccessfulCall(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
    case class CallFailure(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
    case class CallTimeout(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
    case class CircuitOpen(circuitBreakerName: String) extends CircuitBreakerEvent
    case class CircuitHalfOpen(circuitBreakerName: String) extends CircuitBreakerEvent
    case class CircuitClosed(circuitBreakerName: String) extends CircuitBreakerEvent
    case class ShortCircuitedCall(circuitBreakerName: String) extends CircuitBreakerEvent
  }

}
