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
  import cats._
  import cats.data._
  import cats.implicits._

  import talos.circuitbreakers._

  val dummyCircuitBreaker: Unit = ()

  implicit val dummyTalosCircuitBreaker = new TalosCircuitBreaker[Unit, Id] {
    override def name: String = "dummy"

    override def protect[A](task: Id[A]): Id[A] = task

    override def circuitBreaker: Id[Unit] = ()
  }

  import talos.events.TalosEvents

  val circuitBreaker: TalosCircuitBreaker[Unit, Id] = Talos.circuitBreaker[Unit, Id]
```
