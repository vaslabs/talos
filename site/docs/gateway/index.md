---
layout: docs
title: "API Gateway"
position: 5
---

# Talos Gateway

Talos gateway is a prototype API gateway that exploits the talos libraries, akka-http, akka circuit breakers and cats-effect
to protect the backend services using bulkheading and circuit breaker patterns.

## Configuration

This is a prototype but here is a sample configuration if you wish to experiment

```scala
talos {
  gateway {
    // The port the gateway is listening to
    port = 8080,
    //the binding interface for http
    interface = "0.0.0.0",
    // a list of your services behind your talos gateway instance
    services = [
      {
        //whether the gateway should access this service with https
        secure = false,
        //the qualified name of the service
        host = "dogs-service",
        //the port to hit the service
        port = 9000,
        // very simplistic re-write rules
        mappings = [
          {
            //the path to hit on the api gateway. If you wish to carry
            //the remaining path you must post fix it with /*
            //e.g.
            //gateway-path = "/animals/dogs/*"
            //then hitting /animals/dogs/goldenretriever will hit
            //dogs-service:9000/dogs/goldenretriever
            gateway-path = "/animals/dogs",
            methods = [GET]
            target-path = "/dogs/"
          }
        ],
        //the maximum amount of concurrent requests to this backend service
        max-inflight-requests = 32,
        //the timeout after which the circuit breaker will interrupt the calls to your service
        call-timeout = 5 seconds,
        importance = Medium
      },
      //a second service
      {
        secure = false,
        host = "cats-service",
        port = 9001,
        mappings = [
          {
            gateway-path = "/animals/cats",
            methods = [GET]
            target-path = "/cats/"
          }
        ],
        max-inflight-requests = 16,
        call-timeout = 10 seconds,
        // How important is this service, it drives the sensitivity of the circuit breakers. Accepts High, Medium, Low
        importance = High
      }
    ]
  }
}

kamon {
  metric {
    //kamon metric snapshot frequency , helps into seeing hystrix stream results pretty quickly
    tick-interval = 5 seconds
  }
}
```

## Usage

To use the gateway simply base your docker image on the talos gateway and inject your configuration. Something like that

```
FROM vaslabs/talos-gateway

COPY conf /conf/

ENV JAVA_OPTS="-Dconfig.file=/conf/application.conf"
```

## Sandbox example

You can also fork the source code and do
```sh
cd gateway/sandbox
docker-compose up
```

And you get 2 wiremock services, the talos gateway hitting them and a hystrix dashboard.

The sandbox is configured in a way to demonstrate recovery of backend services protected by the circuit breakers via the hystrix dashboard.

Run the integration tests with
```sh
sbt gatling-it:testOnly *MultiserviceTrafficHappySimulation
```

On the hystrix dashboard you should see something like the below

![alt text](/talos/img/hystrix_dashboard_sandbox.gif)
