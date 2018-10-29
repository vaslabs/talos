---
layout: docs
title:  "Events"
number: 1
---


## Talos events

```
This library provides a way to stream events on what's happening in the circuit breakers. You can do:

```scala
import akka.pattern.CircuitBreaker
def circuitBreaker = CircuitBreaker(
        system.scheduler,
        maxFailures = 5,
        callTimeout = 1 second,
        resetTimeout = 5 seconds
)

import talos.events.TalosEvents
val circuitBreakerWithEvents = TalosEvents.wrap(circuitBreaker, "foo")
```
Alternatively if you prefer this
```tut
import akka.actor.ActorSystem

import akka.pattern.CircuitBreaker

import scala.concurrent.duration._

import talos.events.syntax._

def circuitBreaker(implicit actorSystem: ActorSystem) = CircuitBreaker.withEventReporting(
      "myCircuitBreaker",
      actorSystem.scheduler,
      5,
      2 seconds,
      5 seconds
)
```

Now circuit breaker events arrive in the akka event stream.
```tut
import talos.events.TalosEvents.model._
```

You can consume these events and create your own metrics or you can use taloskamon for getting metrics in Kamon out of the box. 

