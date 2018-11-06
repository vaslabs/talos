---
layout: docs
title:  "Events"
number: 1
---


# Talos events

This is the basic documentation for exposing the Akka circuit breaker events. For more abstract solution go to advanced.

## Dependencies

```scala
libraryDependencies ++= Seq(
          "org.vaslabs.talos" %% "talosevents" % "0.1.0",
          "org.vaslabs.talos" %% "talosakkasupport" % "0.1.0"
)
```
## Usage example

This library provides a way to stream events on what's happening in the circuit breakers. You can do:

```tut:silent
import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import cats.effect.IO
import talos.circuitbreakers.TalosCircuitBreaker

import talos.circuitbreakers.akka._

import scala.concurrent.duration._
  def circuitBreaker(implicit actorSystem: ActorSystem): TalosCircuitBreaker[CircuitBreaker, IO] = {
    AkkaCircuitBreaker(
      "testCircuitBreaker",
      5,
      5 seconds,
      10 seconds
    )
  }
```

Now you can use the circuit breaker via the TalosCircuitBreaker type class.
```tut:silent
def circuitBreakerUsage(implicit actorSystem: ActorSystem): Unit = {
    val myCircuitBreaker: TalosCircuitBreaker[CircuitBreaker, IO] = circuitBreaker
    val unsafeCall = IO(println("I'm running inside the circuit breaker"))
    myCircuitBreaker.protect(unsafeCall).unsafeRunSync()
}
```

Now circuit breaker events arrive in the akka event stream.

### Legacy support
If you are working on a legacy code and you don't want to change every method call you can extract the underlying circuit breaker like this
```tut:silent
    def legacyUsage(implicit actorSystem: ActorSystem): Unit = {
        val myCircuitBreaker: TalosCircuitBreaker[CircuitBreaker, IO] = circuitBreaker
        val akkaCircuitBreaker: akka.pattern.CircuitBreaker = myCircuitBreaker.circuitBreaker.unsafeRunSync()
        akkaCircuitBreaker.callWithSyncCircuitBreaker(() => println("I'm running inside the circuit breaker"))
    }
```
And you still get the events through the akka event stream.

ADT of the events under:

```scala
import talos.events.TalosEvents.model._
```

You can consume these events and create your own metrics or you can use taloskamon for getting metrics in Kamon out of the box. 

