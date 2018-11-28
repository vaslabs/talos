package talos.laws

import cats.effect.Effect
import org.scalatest.WordSpecLike

abstract class TalosCircuitBreakerLaws[C, F[_]](implicit F: Effect[F]) extends
  WordSpecLike with MeasurementLaws[C, F] with StateLaws[C, F] {

  "a talos circuit breaker" must {
    "expose measurements" in {
      measuresElapsedTimeInSuccessfulCalls
      measuresElapsedTimeInFailedCalls
      measuresElapsedTimeInTimeouts
    }
    "expose state changes" in {
      exposesCircuitOpenEvent
      exposesCircuitClosedTransition
    }
  }

  "the event bus" must {
    "send events to the subscribers" in {
      canConsumeMessagesPublishedToTheEventBus
    }
  }

}
