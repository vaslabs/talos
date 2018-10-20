package talos.http

import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.server.Directives

trait ServerEventHttpRouter extends
      EventStreamMarshalling
      with Directives { source: CircuitBreakerEventsSource =>

  val route = path("hystrix.stream") {
    get {
      complete(source.main)
    }
  }

}
