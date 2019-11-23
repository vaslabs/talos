package talos.http

import java.time.ZonedDateTime

import io.circe.{Encoder, Json}
import talos.http.CircuitBreakerEventsSource.{CircuitBreakerStats, ExposedEvent, StreamEnded, StreamFailed}

import scala.concurrent.duration.FiniteDuration


object json_adapters {

  import io.circe.generic.semiauto._
  import io.circe.syntax._

  implicit final val zonedDateTimeDecoder = Encoder.instance[ZonedDateTime] {
    (zdt => Json.fromLong(zdt.toInstant.toEpochMilli))
  }

  private implicit final def auto[A](value: A)(implicit encoder: Encoder[A]): Json = value.asJson

  private final val unsetValues: Json = Json.obj(
    "group" -> "akka",
    "type" -> "HystrixCommand",
    "rollingCountCollapsedRequests" -> 0,
    "rollingCountFallbackRejection" -> 0,
    "rollingCountResponsesFromCache" -> 0,
    "rollingCountSemaphoreRejected" -> 0,
    "rollingCountThreadPoolRejected" -> 0,
    "rollingCountBadRequests" -> 0,
    "currentConcurrentExecutionCount" -> 0,
    "propertyValue_circuitBreakerRequestVolumeThreshold" -> 0,
    "propertyValue_circuitBreakerSleepWindowInMilliseconds" -> 0,
    "propertyValue_circuitBreakerErrorThresholdPercentage" -> 0,
    "propertyValue_circuitBreakerForceOpen" -> false,
    "propertyValue_circuitBreakerForceClosed" -> false,
    "propertyValue_executionIsolationStrategy" -> "THREAD",
    "propertyValue_circuitBreakerEnabled" -> true,
    "propertyValue_executionIsolationThreadTimeoutInMilliseconds" -> 0,
    "propertyValue_executionIsolationThreadInterruptOnTimeout" -> true,
    "propertyValue_executionIsolationThreadPoolKeyOverride" -> None,
    "propertyValue_executionIsolationSemaphoreMaxConcurrentRequests" -> 0,
    "propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests" -> 0,
    "propertyValue_requestCacheEnabled" -> false,
    "propertyValue_requestLogEnabled" -> false,
    "reportingHosts" -> 1
  )

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder.encodeLong.contramap(
    _.toMillis
  )

  implicit val circuitBreakerStatsEncoder: Encoder[CircuitBreakerStats] = deriveEncoder[CircuitBreakerStats].mapJson(
    json => json.deepMerge(unsetValues)
  )

  implicit val exposedEventEncoder: Encoder[ExposedEvent] = Encoder.instance {
    case o: CircuitBreakerStats => circuitBreakerStatsEncoder(o)
    case StreamEnded => Json.obj("result" -> "success")
    case StreamFailed(_) => Json.obj("result" -> "failure")
  }


}
