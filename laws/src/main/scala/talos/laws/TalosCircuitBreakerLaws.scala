package talos.laws

import cats.effect.Effect
import org.scalatest.WordSpecLike

abstract class TalosCircuitBreakerLaws[S, C, F[_]](implicit F: Effect[F]) extends
  WordSpecLike with MeasurementLaws[S, C, F] with StateLaws[S, C, F] {

  "a talos circuit breaker" must {
    "send events to the subscribers" in {
      canConsumeMessagesPublishedToTheEventBus
    }
    "expose measurements for successful calls" in {
      measuresElapsedTimeInSuccessfulCalls
    }
    "expose measurements for failed calls" in {
      measuresElapsedTimeInFailedCalls
    }
    "expose measurements for call timeouts" in {
      measuresElapsedTimeInTimeouts
    }

    "expose state changes for open circuit" in {
      exposesCircuitOpenEvent
    }
    "expose state changes for closing circuit" in {
      exposesCircuitClosedTransition
    }
  }


}
