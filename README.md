# talos [![Build Status](https://travis-ci.com/vaslabs/talos.svg?branch=master)](https://travis-ci.com/vaslabs/talos) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.vaslabs.talos/taloskamon_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.vaslabs.talos/taloskamon_2.12) [![Join the chat at https://gitter.im/vaslabs/talos](https://badges.gitter.im/vaslabs/talos.svg)](https://gitter.im/vaslabs/talos?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


Talos is a set of tools for enabling fine grained monitoring of the Akka circuit breakers.

## Usage
Talos is modularised. You can twist it and pick the dependencies that fit your need. But let's go step by step.

```scala
libraryDependencies += "org.vaslabs.talos" %% "talosevents" % "0.0.3"
libraryDependencies += "org.vaslabs.talos" %% "taloskamon" % "0.0.3"
libraryDependencies += "org.vaslabs.talos" %% "hystrixreporter" % "0.0.3"
```
This library provides a way to stream events on what's happening in the circuit breakers. You can do:
```scala
import akka.pattern.CircuitBreaker
import talos.events.syntax._

val circuitBreaker = CircuitBreaker.withEventReporting(
      name,
      actorSystem.scheduler,
      5,
      2 seconds,
      5 seconds
)

```
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


