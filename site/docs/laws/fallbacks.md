---
layout: docs
title:  "Fallbacks"
number: 2
---

## Fallback Law

Fallbacks are also supported by Talos. As suggested from literature[1] the users should involve the stakeholders to define
the course of action upon Circuit Breaker introduced failures. From the engineering perspective this means that the user
should be able to pass a function that will be executed in the case of a failure.

Such action is modeled as:

```scala
    def protectWithFallback[A, E](task: F[A], fallback: F[E]): F[Either[E, A]]
```

The result of the user will be an Either to aid the reasoning of whether a successful call or a fallback was executed.

The law then on fallbacks goes a bit more: We define a fallback as a fast call. This is inspired a bit from
Hystrix[2] which limits the in-flight fallback calls. Instead, at the current stage, Talos is forcing the fallbacks to execute
within 10 milliseconds.

The fallback promises in summary are the following:
- A user can define a fallback, whose result will be stored in the Left side of an Either.
- A fallback has only 10ms time to be completed, otherwise the fallback is rejected and a FallbackRejected event is published.
- Upon a successful fallback execution, a FallbackSuccess event is published.
- If the user defined fallback causes an exception, a FallbackFailure event is published.
- Fallbacks are executed in these scenarios:
    - A protected call failed or timed out.
    - The circuit breaker is tripped.



## References

1. Michael T. Nygard. Release It!: Design and Deploy Production-Ready Software (Pragmatic Programmers). Pragmatic Bookshelf, 2007.
2. https://github.com/Netflix/Hystrix
