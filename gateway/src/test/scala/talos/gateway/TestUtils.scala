package talos.gateway

import akka.http.scaladsl.model.HttpMethods
import talos.gateway.config._

import scala.concurrent.duration._

object TestUtils {

  def gatewayConfiguration = GatewayConfig(
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
        8,
        1 second,
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
        8,
        1 second,
        Medium
      )
    ),
    "0.0.0.0",
    8080
  )

}
