---
layout: docs
title:  "Talos Gateway"
position: 5
---

# Talos Gateway

- Talos gateway is a prototype API gateway that exploits the talos libraries, akka-http, akka circuit breakers and cats-effect
to protect the backend services using bulkheading and circuit breaker patterns.

This is a prototype but here is a sample configuration if you wish to experiment

```scala
talos {
  gateway {
    // The port the gateway is listening to
    port: 8080,
    //the binding interface for http
    interface: "0.0.0.0",
    // a list of your services behind your talos gateway instance
    services: [
      {
        //whether the gateway should access this service with https
        secure: false,
        //the qualified name of the service
        host: "dogs.localhost",
        //the port to hit the service
        port: 9000,
        // very simplistic re-write rules
        mappings: [
          {
            gateway-path: "/dogs",
            methods: [GET]
            target-path: "/animals/dogs/"
          }
        ],
        //the maximum amount of concurrent requests to this backend service
        max-inflight-requests: 32,
        //the timeout after which the circuit breaker will interrupt the calls to your service
        call-timeout: 5 seconds
      },
      //a second service
      {
        secure: false,
        host: "cats.localhost",
        port: 9001,
        mappings: [
          {
            gateway-path: "/cats",
            methods: [GET]
            target-path: "/animals/cats/"
          }
        ],
        max-inflight-requests: 16,
        call-timeout: 10 seconds
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

