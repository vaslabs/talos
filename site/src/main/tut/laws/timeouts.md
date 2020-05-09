---
layout: docs
title:  "Timeouts"
number: 2
---


## The timeout law

A timeout is enforced for every instance implementation of a TalosCircuitBreaker.

```tut:silent
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import cats.effect._

import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import talos.circuitbreakers.akka._


implicit lazy val actorSystem: ActorSystem[_] = ActorSystem(Behaviors.ignore, "ConsoleTest")

lazy val circuitBreaker = AkkaCircuitBreaker(
    "my_circuit_breaker", 5, resetTimeout = 2 seconds, callTimeout = 5 seconds
)

implicit lazy val timerIO = IO.timer(ExecutionContext.global)
lazy val timingOut = IO.sleep(6 seconds)

lazy val protectedCall: IO[Unit] = circuitBreaker.protect(timingOut)

```