package talos.gateway

import akka.http.scaladsl.model.HttpMethods
import talos.gateway.config._

import scala.concurrent.duration._

object TestUtils {

  def gatewayConfiguration(maxInFlightRequests: Int = 8, timeout: FiniteDuration = 1 second) = GatewayConfig(
    List(
      ServiceConfig(
        false,
        "fooservice",
        8080,
        List(
          Mapping(
            "/foo/",
            List(HttpMethods.GET, HttpMethods.POST),
            "/"
          ),
          Mapping(
            "/foobar/",
            List(HttpMethods.GET),
            "/bar"
          )
        ),
        maxInFlightRequests,
        timeout,
        High
      ),
      ServiceConfig(
        false,
        "barservice",
        8080,
        List(
          Mapping(
            "/bar/",
            List(HttpMethods.GET, HttpMethods.POST),
            "/"
          ),
          Mapping(
            "/barfoo/",
            List(HttpMethods.GET),
            "/bar"
          )
        ),
        maxInFlightRequests,
        timeout,
        Medium
      )
    ),
    "0.0.0.0",
    8080
  )

}
