package talos.http

import java.time.ZonedDateTime

object Utils {

  def statsSample =
    CircuitBreakerStatsActor.CircuitBreakerStats(
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
      Map.empty,
      0,
      Map.empty
    )

}