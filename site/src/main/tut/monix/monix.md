---
layout: docs
title:  "Monix"
number: 1
---

# Monix CircuitBreaker

Talos supports two circuit breakers. So far you've found out how to use the Akka one.

To use monix declare the talos monix support dependency.

```scala
libraryDependencies ++= Seq(
          "org.vaslabs.talos" %% "taloscore" % "0.2.0",
          "org.vaslabs.talos" %% "talosmonixsupport" % "0.2.0"
)
```

The difference between the two is that if you are already using monix you can continue composing the Async typeclass of your choice.

You can do something like this.

```tut:silent
import scala.concurrent.duration._

import akka.actor.ActorSystem

import cats.effect._

import monix.catnap.CircuitBreaker

import talos.circuitbreakers._

import talos.circuitbreakers.monix._


def usage[F[_]](implicit actorSystem: ActorSystem, F: Async[F]): TalosCircuitBreaker[CircuitBreaker[F], F] = {
    implicit val effectClock = Clock.create[F]

    val circuitBreaker: CircuitBreaker[F] = CircuitBreaker.unsafe[F](5, 5 seconds)

    MonixCircuitBreaker("testCircuitBreaker", circuitBreaker)
}
```
E.g. for IO you can then do
```tut:silent
def usageWithIO(implicit actorSystem: ActorSystem): IO[Unit] = {
    val unprotectedTask: IO[Unit] = IO(println("I'm running in the circuit breaker"))
    val ioCircuitBreaker = usage[IO]
    ioCircuitBreaker.protect(unprotectedTask)
}
```

Unfortunately you still depend on the actor system as the events are fired to the akka event stream which is the point of
connection with other modules.

Decoupling the event stream is being explored.