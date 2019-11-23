package talos.laws

import cats.effect.Effect
import org.scalatest.WordSpecLike

abstract class TalosCircuitBreakerLaws[C, S, F[_]](implicit F: Effect[F]) extends
  WordSpecLike with MeasurementLaws[C, S, F] with StateLaws[C, S, F] with FallbackLaws[C, S, F] {

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

    "fallback failures are recorded" in {
      fallbackFailureIsLogged
    }

    "fallbacks are not allowed to run heavy operations" in {
      fallbackSlownessIsNotAllowed
      resetCB
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
