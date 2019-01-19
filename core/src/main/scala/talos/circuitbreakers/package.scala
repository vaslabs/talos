package talos

import java.util.concurrent.TimeoutException

package object circuitbreakers {

  trait TalosCircuitBreaker[C, F[_]] {

    def name: String

    def protect[A](task: F[A]): F[A]

    def protectWithFallback[A, E](task: F[A], fallback: F[E]): F[Either[E, A]]

    def circuitBreaker: F[C]

    def eventBus[S](implicit eventBus: EventBus[S]): EventBus[S] = eventBus
  }

  trait EventBus[S] {
    def subscribe[T](subscriber: S, topic: Class[T]): Option[S]

    def unsubsribe(a: S): Unit

    def publish[A <: AnyRef](a: A): Unit
  }


  class FallbackTimeoutError private[circuitbreakers](circuitBreakerName: String) extends TimeoutException {
    override def getMessage: String =
      s"Fallback in $circuitBreakerName was not completed on time. Are you doing IO in fallbacks?"
  }

  object Talos {
    def circuitBreaker[C, F[_]](implicit F: TalosCircuitBreaker[C, F]): TalosCircuitBreaker[C, F] = F
  }
}
