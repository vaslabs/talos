---
layout: docs
title:  "Monix"
number: 1
---

# Monix TaskCircuitBreaker

Talos supports two circuit breakers. So far you've found out how to use the Akka one.

To use monix declare the talos monix support dependency.

```scala
libraryDependencies ++= Seq(
          "org.vaslabs.talos" %% "taloscore" % "0.2.0",
          "org.vaslabs.talos" %% "talosmonixsupport" % "0.2.0"
)
```

The difference between the two is that if you are already using monix you can continue composing Task. You can do something like this.

```tut:silent
import scala.concurrent.duration._

import akka.actor.ActorSystem

import monix.eval.Task

import talos.circuitbreakers.monix._


def usage(implicit actorSystem: ActorSystem): Task[Unit] = {
    val monixCircuitBreaker = MonixCircuitBreaker("testCircuitBreaker", 5 seconds, 5 , 5 seconds)

    val unprotectedTask: Task[Unit] = Task(println("I'm running in the circuit breaker"))

    monixCircuitBreaker.protect(unprotectedTask)
}
```

Unfortunately you still depend on the actor system as the events are fired to the akka event stream which is the point of
connection with other modules.

Decoupling the event stream is being explored.