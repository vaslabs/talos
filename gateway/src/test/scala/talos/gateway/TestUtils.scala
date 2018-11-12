package talos.gateway

import akka.http.scaladsl.model.HttpMethods
import talos.gateway.config.{GatewayConfig, Mapping, ServiceConfig}
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
            List(HttpMethods.GET),
            "/"
          )
        ),
        8,
        1 second
      )
    )
  )

}
