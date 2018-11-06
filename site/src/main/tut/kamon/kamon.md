---
layout: docs
title:  "Kamon metrics"
number: 2
---

# Kamon metrics


## Dependency

```scala
libraryDependencies += "org.vaslabs.talos" %% "taloskamon" % "0.1.0"
```

A typed actor is used to register to the event stream and record stats in Kamon. Thus there are 
two ways to start the actor. 

## Using the untyped actor system

The most common use case is that you are still using the untyped actor system. You can spawn 
an actor in the following ways

### With an actor context
```tut:silent
  import akka.actor.ActorContext
  import akka.actor.typed.ActorRef
  import akka.actor.typed.scaladsl.adapter._

  import talos.events.TalosEvents.model.CircuitBreakerEvent
  import talos.kamon.StatsAggregator
  
  def recordKamonMetrics(ctx: ActorContext): ActorRef[CircuitBreakerEvent] =
    ctx.spawn(StatsAggregator.behavior(), "KamonStatsAggregator")
```

### From the actor system directly

```tut:silent
  import akka.actor.ActorSystem
  
  import akka.actor.typed.ActorRef
  import akka.actor.typed.scaladsl.adapter._
  
  import scala.concurrent.duration._
  import scala.concurrent.Future
  
  import talos.events.TalosEvents.model.CircuitBreakerEvent
  import talos.kamon.StatsAggregator

  def recordKamonMetrics(implicit actorSystem: ActorSystem): Future[ActorRef[CircuitBreakerEvent]] =
    actorSystem.toTyped.systemActorOf(StatsAggregator.behavior(), "KamonStatsAggregator")(2 seconds)
```

## Using the typed actor system

There is no need for implicit resolution, you can start the actor from any of your behaviours following 
the Akka typed documentation.

The actor is adding metrics to Kamon directly. 

In the future the underlying actor will be encapsulated better.

## Metrics format

Now you can get counters and histograms recorded in Kamon in the following format:
- Counters

`name="circuit-breaker-<circuit breaker name>", tags={eventType=[successful-call][failed-call][circuit-open][circuit-closed][circuit-half-open][call-timeout][short-circuited]}`

- Histograms

`name="circuit-breaker-elapsed-time-", tags={eventType=[successful-call][failed-call][call-timeout]}`

You can now use any Kamon reported library of your preference or you can import the talos Hystrix reporter which gives you the ability to display fine grained circuit breaker stats into a hystrix dashboard.
