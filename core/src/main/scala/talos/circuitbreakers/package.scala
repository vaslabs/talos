package talos

package object circuitbreakers {

  trait TalosCircuitBreaker[C, F[_]] {

    def name: String

    def protect[A](task: F[A]): F[A]

    def circuitBreaker: F[C]

    def eventBus[S](implicit eventBus: EventBus[S]): EventBus[S] = eventBus
  }

  trait EventBus[S] {
    def subscribe[T](subscriber: S, topic: Class[T]): Option[S]

    def unsubsribe(a: S): Unit

    def publish[A <: AnyRef](a: A): Unit
  }

  object Talos {
    def circuitBreaker[C, F[_]](implicit F: TalosCircuitBreaker[C, F]): TalosCircuitBreaker[C, F] = F
  }
}
