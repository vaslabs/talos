package talos.laws

import cats.effect.Effect
import org.scalatest.WordSpecLike

abstract class TalosCircuitBreakerLaws[S, C, F[_]](implicit F: Effect[F]) extends
  WordSpecLike with MeasurementLaws[S, C, F] with StateLaws[S, C, F] with FallbackLaws[S, C, F] {

  "a talos circuit breaker" must {
    "send events to the subscribers" in {
      canConsumeMessagesPublishedToTheEventBus
    }
    "expose measurements for successful calls" in {
      measuresElapsedTimeInSuccessfulCalls
    }

    "execute user defined fallbacks on error" in {
      fallbackActivatedOnError
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

    "short circuit calls" in {
      callsAreShortCircuitedFromNowOn
    }

    "run fallbacks when circuit is open" in {
      fallbackExpected
    }

    "expose state changes for closing circuit" in {
      exposesCircuitClosedTransition
    }
  }

}
