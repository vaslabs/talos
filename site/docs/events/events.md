---
layout: docs
title:  "Events"
number: 1
---


# Talos events

This is the basic documentation for exposing the Akka circuit breaker events. For more abstract solution go to advanced.

## Dependencies

```sbt
libraryDependencies ++= Seq(
          "org.vaslabs.talos" %% "taloscore" % "1.0.0",
          "org.vaslabs.talos" %% "talosakkasupport" % "1.0.0"
)
```
## Usage example

This library provides a way to stream events on what's happening in the circuit breakers. You can do:

```scala mdoc:silent
import akka.actor.typed.{ActorSystem, ActorRef}
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.CircuitBreaker
import cats.effect.IO
import talos.circuitbreakers.TalosCircuitBreaker
import talos.http.CircuitBreakerEventsSource.CircuitBreakerStats
import talos.circuitbreakers.akka._

import scala.concurrent.duration._


def createCircuitBreaker(name: String = "testCircuitBreaker")
                      (implicit system: ActorSystem[_]): AkkaCircuitBreaker.Instance = {
    AkkaCircuitBreaker(
      name,
      CircuitBreaker(
        system.scheduler.toClassic,
        5,
        2 seconds,
        5 seconds
      )
    )
}

```

Now you can use the circuit breaker via the TalosCircuitBreaker type class.
```scala mdoc:silent
import talos.events.TalosEvents.model.CircuitBreakerEvent
def circuitBreakerUsage(implicit actorSystem: ActorSystem[_]): Unit = {
    val myCircuitBreaker: TalosCircuitBreaker[CircuitBreaker, ActorRef[CircuitBreakerEvent], IO] = createCircuitBreaker()
    val unsafeCall = IO(println("I'm running inside the circuit breaker"))
    myCircuitBreaker.protect(unsafeCall).unsafeRunSync()
}
```

Now circuit breaker events arrive in the akka event stream.

### Legacy support
If you are working on a legacy code and you don't want to change every method call you can extract the underlying circuit breaker like this
```scala mdoc:silent
    def legacyUsage(implicit actorSystem: ActorSystem[_]): Unit = {
        val myCircuitBreaker: TalosCircuitBreaker[CircuitBreaker, ActorRef[CircuitBreakerEvent], IO] = createCircuitBreaker()
        val akkaCircuitBreaker: akka.pattern.CircuitBreaker = myCircuitBreaker.circuitBreaker.unsafeRunSync()
        akkaCircuitBreaker.callWithSyncCircuitBreaker(() => println("I'm running inside the circuit breaker"))
    }
```
And you still get the events through the akka event stream.

ADT of the events under:

```scala mdoc:silent
import talos.events.TalosEvents.model._
```

You can consume these events and create your own metrics or you can use taloskamon for getting metrics in Kamon out of the box. 

