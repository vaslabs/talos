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
          "org.vaslabs.talos" %% "taloscore" % "0.5.1",
          "org.vaslabs.talos" %% "talosmonixsupport" % "0.5.1"
)
```

The difference between the two is that if you are already using monix you can
continue composing the Async typeclass of your choice.

You can do something like this.

```tut:silent
import scala.concurrent.duration._

import akka.actor.ActorSystem

import cats.effect._

import monix.catnap.CircuitBreaker

import talos.circuitbreakers._

import talos.circuitbreakers.monix._


def usage[F[_]](implicit actorSystem: ActorSystem, F: Concurrent[F]): TalosCircuitBreaker[CircuitBreaker[F], F] = {
    implicit val effectClock = Clock.create[F]

    val circuitBreaker: CircuitBreaker[F] = CircuitBreaker.unsafe[F](5, 5 seconds)

    MonixCircuitBreaker("testCircuitBreaker", circuitBreaker, callTimeout = 2 seconds)
}
```
E.g. for IO you can then do
```tut:silent
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

def usageWithIO(implicit actorSystem: ActorSystem): IO[Unit] = {
    implicit val timeoutContextShift = IO.contextShift(
        ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    )

    val unprotectedTask: IO[Unit] = IO(println("I'm running in the circuit breaker"))
    val ioCircuitBreaker = usage[IO]
    ioCircuitBreaker.protect(unprotectedTask)
}
```

Unfortunately you still depend on the actor system as the events are fired to the akka event stream which is the point of
connection with other modules.

Decoupling the event stream is being explored.