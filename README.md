# talos [![Build Status](https://travis-ci.com/vaslabs/talos.svg?branch=master)](https://travis-ci.com/vaslabs/talos)

Talos is a set of tools for enabling fine grained monitoring of the Akka circuit breakers.

## Usage
Talos is modularised. You can twist it and pick the dependencies that fit your need. But let's go step by step.

### Talos events
```scala
libraryDependencies += "org.vaslabs.talos" %% "talosevents" % "0.0.2"
```
This library provides a way to stream events on what's happening in the circuit breakers. You can do:
```scala
import akka.pattern.CircuitBreaker

val circuitBreaker = CircuitBreaker(
        system.scheduler,
        maxFailures = 5,
        callTimeout = 1 second,
        resetTimeout = 5 seconds
)
import talos.events.TalosEvents
TalosEvents.wrap(circuitBreaker, "foo")
```

Now circuit breaker events arrive in the akka event stream.
```scala
case class SuccessfulCall(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
case class CallFailure(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
case class CallTimeout(circuitBreakerName: String, elapsedTime: FiniteDuration) extends CircuitBreakerEvent
case class CircuitOpen(circuitBreakerName: String) extends CircuitBreakerEvent
case class CircuitHalfOpen(circuitBreakerName: String) extends CircuitBreakerEvent
case class CircuitClosed(circuitBreakerName: String) extends CircuitBreakerEvent
case class ShortCircuitedCall(circuitBreakerName: String) extends CircuitBreakerEvent
```
You can consume this events and create your own metrics or you can use taloskamon for getting metrics in Kamon out of the box.
```scala
libraryDependencies += "org.vaslabs.talos" %% "taloskamon" % "0.0.2"
```
Now you get counters and histograms recorded in Kamon in the following format:
- Counters

`name="circuit-breaker-<circuit breaker name>", tags={eventType=[successful-call][failed-call][circuit-open][circuit-closed][circuit-half-open][call-timeout][short-circuited]}`

- Histograms

`name="circuit-breaker-elapsed-time-", tags={eventType=[successful-call][failed-call][call-timeout]}`

You can now use any Kamon reported library of your preference or you can import the talos Hystrix reporter which gives you the ability to display fine grained circuit breaker stats into a hystrix dashboard like this:

- Dependency
```scala
libraryDependencies += "org.vaslabs.talos" %% "hystrixreporter" % "0.0.2"
```
```scala
implicit val actorSystem: ActorSystem = ActorSystem("TalosExample")

implicit val clock: Clock = Clock.systemUTC()

implicit val actorSystemTimeout: Timeout = Timeout(2 seconds)

val server = new HystrixReporterServer("localhost", 8080)

val startingServer = server.start(clock)
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


