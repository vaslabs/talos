# talos [![Build Status](https://travis-ci.com/vaslabs/talos.svg?branch=master)](https://travis-ci.com/vaslabs/talos) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.vaslabs.talos/taloskamon_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.vaslabs.talos/talosevents_2.12) [![Join the chat at https://gitter.im/vaslabs/talos](https://badges.gitter.im/vaslabs/talos.svg)](https://gitter.im/vaslabs/talos?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/d0dbf73a127c4eff9a5e62d9fa628cbd)](https://app.codacy.com/app/vaslabs/talos?utm_source=github.com&utm_medium=referral&utm_content=vaslabs/talos&utm_campaign=Badge_Grade_Dashboard) [![Codacy Badge](https://api.codacy.com/project/badge/Coverage/ae86edbdde884633a0417d851e4fcc9a)](https://www.codacy.com/app/vaslabs/talos?utm_source=github.com&utm_medium=referral&utm_content=vaslabs/talos&utm_campaign=Badge_Coverage)


Talos is a set of tools for enabling fine grained monitoring of the Akka circuit breakers. For comprehensive [documentation](https://vaslabs.github.io/talos/events/events.html)

## Usage
Talos is modularised. You can twist it and pick the dependencies that fit your need. But let's go step by step.

```scala
libraryDependencies += "org.vaslabs.talos" %% "talosevents" % "0.1.0"
libraryDependencies += "org.vaslabs.talos" %% "talosakkasupport" % "0.1.0"
libraryDependencies += "org.vaslabs.talos" %% "taloskamon" % "0.1.0"
libraryDependencies += "org.vaslabs.talos" %% "hystrixreporter" % "0.1.0"
```
This library provides a way to stream events on what's happening in the circuit breakers. You can do:
```scala
import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import cats.effect.IO
import talos.circuitbreakers.TalosCircuitBreaker
import talos.circuitbreakers.akka.AkkaCircuitBreaker

val talosCircuitBreaker: TalosCircuitBreaker[CircuitBreaker, IO] = AkkaCircuitBreaker(
  name,
  CircuitBreaker(
    actorSystem.scheduler,
    5,
    2 seconds,
    5 seconds
  )
)

```


If you are using this library on an existing solution and you need to extract the akka circuit breaker you can do
```scala
    val akkaCB: CircuitBreaker = talosCircuitBreaker.circuitBreaker.unsafeRunSync()
```

Otherwise you can use the TalosCircuitBreaker typeclass directly
```scala
    val action: IO[Unit] = talosCircuitBreaker.protect(IO(println("Running inside the circuit breaker")))
    action.unsafeRunSync()
```

Talos also supports the TaskCircuitBreaker from [monix](https://vaslabs.github.io/talos/monix/monix.html)

Get an akka directive
```scala
import akka.http.scaladsl.server.Route

val hystrixReporterDirective: Route  = new HystrixReporterDirective().hystrixStreamHttpRoute.run(Clock.systemUTC())
```
And you can mix the Akka directive with the rest of your application.

The example below shows a complete server start 
```scala
implicit val actorSystem: ActorSystem = ActorSystem("TalosExample")

implicit val actorSystemTimeout: Timeout = Timeout(2 seconds)
import talos.http._
val hystrixStreamRoute = new HystrixReporterDirective().hystrixStreamHttpRoute
val server = new StartServer("0.0.0.0", 8080)
val startingServer = (hystrixReporterDirective andThen server.startHttpServer).run(Clock.systemUTC())

```

Now you can consume the hystrix stream from http://localhost:8080/hystrix.stream and point the hystrix dashboard to it.

### Complete usage example

How to code can be found here:
https://github.com/vaslabs/talos/blob/master/examples/src/main/scala/talos/examples/ExampleApp.scala

## Run the demo

- Spin up the docker images provided: 

```bash
cd examples
docker-compose up
```

- Then go to a browser and navigate to (http://localhost:7979/hystrix-dashboard/)
You should see this
![alt_text](https://user-images.githubusercontent.com/3875429/47372906-a4c30f80-d6e2-11e8-8219-0a01a464ba11.png)

- The address of the stream is http://talos-demo:8080/hystrix.stream

- Click add stream and then monitor stream.

![alt text](https://user-images.githubusercontent.com/3875429/47429624-dc879100-d78e-11e8-856a-15ca3855a2eb.gif)

## Architecture

![alt text](https://docs.google.com/drawings/d/e/2PACX-1vRKebbVROyBITii1GHHigPvGbFt0QdEIzk5oT1mZa16VN30MYH4wvhqd14Qllp_1SIz3wcqDdAP5Kx6/pub?w=1440&h=1080)


