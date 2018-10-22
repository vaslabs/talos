package talos.http

import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}

class JsonSpec extends FlatSpec with Matchers{

  def assertNotNull(json: Json, key: String) = {
    println(s"Testing $key")
    json.findAllByKey(key).size should be > 0
  }

  it should "pass the validation checks of hystrix dashboard" in {
    import json_adapters._
    val statsObject = Utils.statsSample

    import io.circe.syntax._
    val data = statsObject.asJson

    assertNotNull(data, "reportingHosts")
    assertNotNull(data, "type")
    assertNotNull(data, "name")
    assertNotNull(data, "group")
    // assertNotNull(data,"currentTime");
    assertNotNull(data, "isCircuitBreakerOpen")
    assertNotNull(data, "errorPercentage")
    assertNotNull(data, "errorCount")
    assertNotNull(data, "requestCount")
    assertNotNull(data, "rollingCountCollapsedRequests")
    assertNotNull(data, "rollingCountExceptionsThrown")
    assertNotNull(data, "rollingCountFailure")
    assertNotNull(data, "rollingCountFallbackFailure")
    assertNotNull(data, "rollingCountFallbackRejection")
    assertNotNull(data, "rollingCountFallbackSuccess")
    assertNotNull(data, "rollingCountResponsesFromCache")
    assertNotNull(data, "rollingCountSemaphoreRejected")
    assertNotNull(data, "rollingCountShortCircuited")
    assertNotNull(data, "rollingCountSuccess")
    assertNotNull(data, "rollingCountThreadPoolRejected")
    assertNotNull(data, "rollingCountTimeout")
    assertNotNull(data, "rollingCountBadRequests")
    assertNotNull(data, "currentConcurrentExecutionCount")
    assertNotNull(data, "latencyExecute_mean")
    assertNotNull(data, "latencyExecute")
    assertNotNull(data, "propertyValue_circuitBreakerRequestVolumeThreshold")
    assertNotNull(data, "propertyValue_circuitBreakerSleepWindowInMilliseconds")
    assertNotNull(data, "propertyValue_circuitBreakerErrorThresholdPercentage")
    assertNotNull(data, "propertyValue_circuitBreakerForceOpen")
    assertNotNull(data, "propertyValue_circuitBreakerForceClosed")
    assertNotNull(data, "propertyValue_executionIsolationStrategy")
    assertNotNull(data, "propertyValue_executionIsolationThreadTimeoutInMilliseconds")
    assertNotNull(data, "propertyValue_executionIsolationThreadInterruptOnTimeout")
    assertNotNull(data, "propertyValue_executionIsolationSemaphoreMaxConcurrentRequests")
    assertNotNull(data, "propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests")
    assertNotNull(data, "propertyValue_requestCacheEnabled")
    assertNotNull(data, "propertyValue_requestLogEnabled")
    assertNotNull(data, "propertyValue_metricsRollingStatisticalWindowInMilliseconds")
  }

}
