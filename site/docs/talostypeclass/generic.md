---
layout: docs
title:  "Other circuit breakers"
number: 2
---

# Generic support

Although a bit crude at the moment, a typeclass is provided to support any circuit breakers as long as they can obey the
TalosCircuitBreaker definition.

More or less you can do

```scala mdoc:silent
    import talos.events.TalosEvents
    import talos.circuitbreakers._

    type Id[A] = A

    val dummyCircuitBreaker = ()
    val dummySubscriber = ()

    implicit val dummyTalosCircuitBreaker = new TalosCircuitBreaker[Unit, Unit, Id] {
      override def name: String = "dummy"

      override def protect[A](task: Id[A]): Id[A] = task

      override def circuitBreaker: Id[Unit] = ()

      override def protectWithFallback[A, E](task: Id[A], fallback: Id[E]): Id[Either[E, A]] = Right(task)

      override def eventBus: EventBus[Unit] = ???
    }

    val circuitBreaker: TalosCircuitBreaker[Unit, Unit, Id] = Talos.circuitBreaker[Unit, Unit, Id]
```
