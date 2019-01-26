---
layout: docs
title: "Laws"
---

# Talos Laws

The laws are inspired from various sources:

- [...]timeouts at the interfaces between remote systems to prevent the failure of a
single component from bringing down all components[...][more](https://doc.akka.io/docs/akka/2.5/common/circuitbreaker.html#why-are-they-used-)
- [...] it is essential to involve the system’s stakeholders when deciding how to handle calls made when the circuit is open.
    Michael T. Nygard. Release It!: Design and Deploy Production-Ready Software (Pragmatic Programmers). Pragmatic Bookshelf, 2007.
- Circuit breakers are a valuable place for monitoring. Any change in breaker state should be logged and breakers should reveal details of their state for deeper monitoring. [Martin Fowler](https://martinfowler.com/bliki/CircuitBreaker.html
)


Taking it from there we can now derive (in the order given) the following laws.

- A circuit breaker must enforce a timeout.
- Upon catching a failure in the circuit breaker we need to have a user defined fallback to execute.
- Circuit breakers must expose their state for monitoring purposes.


A laws library is provided to be able to:
- Test these laws uniformly in the provided implementations (Based on Monix-catnip and Akka).
- Test user implementations of the TalosCircuitBreaker.


```scala
libraryDependencies += "org.vaslabs.talos" %% "taloslaws" % "0.6.0"
```

Navigate the laws section to find more.