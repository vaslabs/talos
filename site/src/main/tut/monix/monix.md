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
          "org.vaslabs.talos" %% "taloscore" % "1.0.0",
          "org.vaslabs.talos" %% "talosmonixsupport" % "1.0.0"
)
```

The difference between the two is that if you are already using monix you can
continue composing the Async typeclass of your choice.

You can do something like this.

```scala
import cats.effect._
import monix.catnap.CircuitBreaker

import talos.circuitbreakers._

import talos.circuitbreakers.monix._

import scala.concurrent.duration._

def usage[F[_]](implicit F: Concurrent[F]): MonixCircuitBreaker.Instance[F] = {
    implicit def contextShift: ContextShift[F] = ???
    def circuitBreaker: CircuitBreaker[F] = ???

    MonixCircuitBreaker(
      "testCircuitBreaker",
      circuitBreaker,
      4 seconds
    )
}
```
E.g. for IO you can then do
```scala
import talos.circuitbreakers.monix.MonixCircuitBreaker
import monix.catnap.CircuitBreaker

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import cats.effect._

def usageWithIO(): IO[Unit] = {
    implicit val contextShift = IO.contextShift(ExecutionContext.global)

    implicit val effectClock = Clock.create[IO]

    val circuitBreaker: CircuitBreaker[IO] =
        CircuitBreaker.of[IO](5, resetTimeout = 30 seconds).unsafeRunSync()

    val unprotectedTask: IO[Unit] = IO(println("I'm running in the circuit breaker"))
    val ioCircuitBreaker = MonixCircuitBreaker(
         "testCircuitBreaker",
         circuitBreaker,
         4 seconds
    )

    ioCircuitBreaker.protect(unprotectedTask)
}
```