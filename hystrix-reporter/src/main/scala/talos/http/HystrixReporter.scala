package talos.http

import java.time.ZonedDateTime
import java.util

import akka.actor.{Actor, Props}
import talos.http.HystrixReporter.{CircuitBreakerStats, FetchHystrixEvents}

import scala.collection.mutable.ListBuffer


object HystrixReporter {
  def props: Props = Props(new HystrixReporter)


  case object FetchHystrixEvents

  case class HystrixDashboardEvent()


  case class CircuitBreakerStats
    (
      name: String,
      requestCount: Long,
      currentTime: ZonedDateTime,
      isCircuitBreakerOpen: Boolean,
      errorPercentage: Float,
      errorCount: Long,
      rollingCountFailure: Long,
      rollingCountExceptionsThrown: Long,
      rollingCountShortCircuited: Long,
      rollingCountSuccess: Long,
      latencyExecute_mean: Long,
      latencyExecute: Map[String, Long],
      latencyTotal_mean: Map[String, Long],
      latencyTotal: Map[String, Long],
      reportingHosts: Int = 1
    )

}

class HystrixReporter extends Actor {

  private[this] val deque: util.ArrayDeque[CircuitBreakerStats] = new util.ArrayDeque()

  override def receive: Receive = {
    case cbs: CircuitBreakerStats =>
      deque.addFirst(cbs)
    case FetchHystrixEvents =>
      var listBuffer: ListBuffer[CircuitBreakerStats] = ListBuffer.empty
      var cbs = deque.pollLast()
      while (cbs != null) {
         listBuffer = cbs +: listBuffer
         cbs = deque.pollLast()
      }
      sender() ! listBuffer.toList
  }
}
