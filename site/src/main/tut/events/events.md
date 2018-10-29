---
layout: docs
title:  "Events"
number: 1
---


## Talos events

This library provides a way to stream events on what's happening in the circuit breakers. You can do:

```tut:silent
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
```scala
import talos.events.TalosEvents.model._
```

You can consume these events and create your own metrics or you can use taloskamon for getting metrics in Kamon out of the box. 

