# talos [![Build Status](https://travis-ci.com/vaslabs/talos.svg?branch=master)](https://travis-ci.com/vaslabs/talos) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.vaslabs.talos/taloscore_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.vaslabs.talos/taloscore_2.12) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/d0dbf73a127c4eff9a5e62d9fa628cbd)](https://app.codacy.com/app/vaslabs/talos?utm_source=github.com&utm_medium=referral&utm_content=vaslabs/talos&utm_campaign=Badge_Grade_Dashboard) [![Codacy Badge](https://api.codacy.com/project/badge/Coverage/ae86edbdde884633a0417d851e4fcc9a)](https://www.codacy.com/app/vaslabs/talos?utm_source=github.com&utm_medium=referral&utm_content=vaslabs/talos&utm_campaign=Badge_Coverage) [![Docker hub](https://img.shields.io/badge/Api%20gateway-1.0.0-blue.svg)](https://hub.docker.com/r/vaslabs/talos-gateway/) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Talos is enforcing some theory from literature concerning circuit breakers in the form of typeclasses and laws.

Read more around the theory [here](https://vaslabs.github.io/talos/laws/index.html)


The main deliverable of Talos is fine grained monitoring.

## Usage
Talos is modularised. You can twist it and pick the dependencies that fit your need. But let's go step by step.

```scala
libraryDependencies += "org.vaslabs.talos" %% "taloscore" % "1.0.0"
libraryDependencies += "org.vaslabs.talos" %% "talosakkasupport" % "1.0.0"
libraryDependencies += "org.vaslabs.talos" %% "taloskamon" % "1.0.0"
libraryDependencies += "org.vaslabs.talos" %% "hystrixreporter" % "1.0.0"
```
The events library provides a way to stream events on what's happening in the circuit breakers. E.g. combining with the talosakkasupport you can do:
```scala
import akka.actor.typed.{ActorSystem, ActorRef}
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.CircuitBreaker
import cats.effect.IO
import talos.circuitbreakers.TalosCircuitBreaker
import talos.circuitbreakers.akka._

import scala.concurrent.duration._

def createCircuitBreaker(name: String = "testCircuitBreaker")
                      (implicit system: ActorSystem[_]): AkkaCircuitBreaker.Instance = {
    AkkaCircuitBreaker(
      name,
      CircuitBreaker(
        system.scheduler.toClassic,
        5,
        2 seconds,
        5 seconds
      )
    )
}


```


If you have an existing solution based on Akka circuit breaker and you can extract the circuit breaker like this.
```scala
    val akkaCB: CircuitBreaker = talosCircuitBreaker.circuitBreaker.unsafeRunSync()
```

Otherwise you can use the TalosCircuitBreaker typeclass directly
```scala
    val action: IO[Unit] = talosCircuitBreaker.protect(IO(println("Running inside the circuit breaker")))
    action.unsafeRunSync()
```

Talos also supports the CircuitBreaker from [monix](https://vaslabs.github.io/talos/monix/monix.html)

### Shipping circuit breaker events with hystrix reporter

Get an akka directive
```scala
import akka.http.scaladsl.server.Route

val hystrixReporterDirective: Route  = new HystrixReporterDirective().hystrixStreamHttpRoute.run(Clock.systemUTC())
```
And you can mix the Akka directive with the rest of your application.

### Complete usage example

How to code can be found here:
https://github.com/vaslabs/talos/blob/master/examples/src/main/scala/talos/examples/ExampleApp.scala

### Laws
If you wish to implement your own TalosCircuitBreaker typeclasses you can test them against the laws library:
```scala
libraryDependencies += "org.vaslabs.talos" %% "taloslaws" % "1.0.0" % Test
```


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

![alt text](https://docs.google.com/drawings/d/e/2PACX-1vRKebbVROyBITii1GHHigPvGbFt0QdEIzk5oT1mZa16VN30MYH4wvhqd14Qllp_1SIz3wcqDdAP5Kx6/pub?w=960&h=720)


