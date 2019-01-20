---
layout: docs
title:  "Timeouts"
number: 2
---


## The timeout law

A timeout is enforced for every instance implementation of a TalosCircuitBreaker.

```tut
import akka.actor.ActorSystem
import cats.effect._

import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import talos.circuitbreakers.akka._


implicit val actorSystem: ActorSystem = ActorSystem("ConsoleTest")

val circuitBreaker = AkkaCircuitBreaker("my_circuit_breaker", 5, resetTimeout = 2 seconds, callTimeout = 5 seconds)

implicit val timerIO = IO.timer(ExecutionContext.global)
val timingOut = IO.sleep(6 seconds)

val protectedCall: IO[Unit] = circuitBreaker.protect(timingOut)
Try(protectedCall.unsafeRunSync())

actorSystem.terminate()
```