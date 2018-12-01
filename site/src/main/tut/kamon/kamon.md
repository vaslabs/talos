---
layout: docs
title:  "Kamon metrics"
number: 2
---

# Kamon metrics


## Dependency

```scala
libraryDependencies += "org.vaslabs.talos" %% "taloskamon" % "0.5.0"
```

```tut:silent
  import akka.actor.ActorSystem

  import talos.kamon.StatsAggregator
  
  def recordKamonMetrics(implicit actorSystem: ActorSystem) = StatsAggregator.kamon()
```

## Metrics format

Now you can get counters and histograms recorded in Kamon in the following format:
- Counters

`name="circuit-breaker-<circuit breaker name>", tags={eventType=[successful-call][failed-call][circuit-open][circuit-closed][circuit-half-open][call-timeout][short-circuited]}`

- Histograms

`name="circuit-breaker-elapsed-time-", tags={eventType=[successful-call][failed-call][call-timeout]}`

You can now use any Kamon reported library of your preference or you can import the talos Hystrix reporter which gives you the ability to display fine grained circuit breaker stats into a hystrix dashboard.
