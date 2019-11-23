package talos

import java.util.concurrent.TimeoutException

package object circuitbreakers {

  trait TalosCircuitBreaker[C, S, F[_]] {

    def name: String

    def protect[A](task: F[A]): F[A]

    def protectWithFallback[A, E](task: F[A], fallback: F[E]): F[Either[E, A]]

    def circuitBreaker: F[C]

    def eventBus: EventBus[S]
  }

  object TalosCircuitBreaker {
    import scala.concurrent.duration._
    final val FAST_FALLBACK_DURATION = 10 milli
  }

  trait EventBus[S] {
    def subscribe(subscriber: S): Option[S]

    def unsubsribe(a: S): Unit

    def publish[A <: AnyRef](a: A): Unit
  }


  class FallbackTimeoutError private[circuitbreakers](circuitBreakerName: String) extends TimeoutException {
    override def getMessage: String =
      s"Fallback in $circuitBreakerName was not completed on time. Are you doing IO in fallbacks?"
  }

  object Talos {
    def circuitBreaker[C, S, F[_]](implicit F: TalosCircuitBreaker[C, S, F]): TalosCircuitBreaker[C, S, F] = F
  }
}
