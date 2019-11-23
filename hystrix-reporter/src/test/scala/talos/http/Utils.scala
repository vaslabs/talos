package talos.http

import java.time.ZonedDateTime

import talos.http.CircuitBreakerEventsSource.CircuitBreakerStats

object Utils {

  import scala.concurrent.duration._

  def statsSample =
    CircuitBreakerStats(
      "myCircuitBreaker",
      0L,
      ZonedDateTime.now(),
      false,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0,
      0 nanos,
      Map.empty,
      0 nanos,
      Map.empty,
      1 second
    )

}