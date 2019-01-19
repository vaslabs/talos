package talos.events

import scala.concurrent.duration._

object TalosEvents {

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

    case class FallbackActivated(circuitBreakerName: String) extends CircuitBreakerEvent
  }

}
