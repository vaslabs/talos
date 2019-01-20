---
layout: docs
title:  "Other circuit breakers"
number: 2
---

# Generic support

Although a bit crude at the moment, a typeclass is provided to support any circuit breakers as long as they can obey the
TalosCircuitBreaker definition.

More or less you can do

```tut:silent
    import talos.events.TalosEvents
    import talos.circuitbreakers._

    type Id[A] = A

    val dummyCircuitBreaker = ()

    implicit val dummyTalosCircuitBreaker = new TalosCircuitBreaker[Unit, Id] {
      override def name: String = "dummy"

      override def protect[A](task: Id[A]): Id[A] = task

      override def circuitBreaker: Id[Unit] = ()

      override def protectWithFallback[A, E](task: Id[A], fallback: Id[E]): Id[Either[E, A]] = Right(task)
    }

  val circuitBreaker: TalosCircuitBreaker[Unit, Id] = Talos.circuitBreaker[Unit, Id]
```
