---
layout: docs
title:  "Metrics visualisation"
number: 3
---

## Talos Hystrix Reporter

The Talos hystrix reporter sends the metrics over http in a format compatible with hystrix dashboard.
In the future there may be another dashboard but for now it was the fastest way to 
deliver fine grained visualisations.


### Usage

```sbt
libraryDependencies += "org.vaslabs.talos" %% "hystrixreporter" % "1.0.0"
```

Get an akka directive

```tut:silent
import java.time.Clock

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.server.Route

import talos.http.HystrixReporterDirective

def hystrixReporterDirective(implicit actorContext: ActorContext[_], clock: Clock): Route  =
    new HystrixReporterDirective().hystrixStreamHttpRoute
```

So far Talos is opinionated over Akka but the plan is to make it more generic in the future.
