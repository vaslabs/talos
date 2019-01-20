---
layout: docs
title:  "Monitoring"
number: 2
---

## The monitoring law

Talos is enforcing the Circuit Breaker implementation to expose their state changes as events.
Although both provided Monix and Akka implementations expose those events in the Akka EventStream, it is up to
the user how their implementations will expose such events.

The law check requires the user to provide the way such events can be read.


Every successful call generates a success event with the elapsed time that it took to complete such event.

Subscription to such events can be done via the provided event bus.

```tut:silent
import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout

import cats.effect._

import scala.concurrent.duration._
import talos.circuitbreakers.akka._
import talos.events.TalosEvents.model._

import scala.concurrent.ExecutionContext
import scala.concurrent.Await

implicit val actorSystem: ActorSystem = ActorSystem("ConsoleTest")

val eventListenerBehavior: Behavior[CircuitBreakerEvent] = Behaviors.receiveMessage {
  event =>
    println(event)
    Behaviors.same
}

val eventListener =
  Await.result(
    actorSystem.toTyped.systemActorOf(eventListenerBehavior, "eventListener")(Timeout(3 seconds)),
    3 seconds
)

val circuitBreaker = AkkaCircuitBreaker("my_circuit_breaker", 5, 2 seconds, 5 seconds)

implicit val akkaEventBus = new AkkaEventBus()

circuitBreaker.eventBus.subscribe(eventListener.toUntyped, classOf[CircuitBreakerEvent])

implicit val timerIO = IO.timer(ExecutionContext.global)
val timingOut = IO.unit
```

```tut:silent
val protectedCall: IO[Unit] = circuitBreaker.protect(timingOut)
protectedCall.unsafeRunSync()

actorSystem.terminate()

```

The events that expose the circuit breaker activity and state changes are the following:

```tut:silent
sealed trait CircuitBreakerEvent {
      val circuitBreakerName: String
    }

    case class SuccessfulCall(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
    case class CallFailure(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
    case class CallTimeout(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
    case class CircuitOpen(circuitBreakerName: String) extends CircuitBreakerEvent
    case class CircuitHalfOpen(circuitBreakerName: String) extends CircuitBreakerEvent
    case class CircuitClosed(circuitBreakerName: String) extends CircuitBreakerEvent
    case class ShortCircuitedCall(circuitBreakerName: String) extends CircuitBreakerEvent

    case class FallbackSuccess(circuitBreakerName: String) extends CircuitBreakerEvent
    case class FallbackFailure(circuitBreakerName: String) extends CircuitBreakerEvent
    case class FallbackRejected(circuitBreakerName: String) extends CircuitBreakerEvent
```

