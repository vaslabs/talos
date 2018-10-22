package talos.http

import java.time.ZonedDateTime
import java.util

import akka.actor.{Actor, Props}
import talos.http.CircuitBreakerStatsActor.{CircuitBreakerStats, FetchHystrixEvents, HystrixDashboardEvent}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration


object CircuitBreakerStatsActor {
  def props: Props = Props(new CircuitBreakerStatsActor)


  case object FetchHystrixEvents

  case class HystrixDashboardEvent(stats: List[CircuitBreakerStats])


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
      rollingCountTimeout: Long,
      rollingCountShortCircuited: Long,
      rollingCountSuccess: Long,
      latencyExecute_mean: FiniteDuration,
      latencyExecute: Map[String, FiniteDuration],
      latencyTotal_mean: FiniteDuration,
      latencyTotal: Map[String, FiniteDuration],
      reportingHosts: Int = 1
    )

}

class CircuitBreakerStatsActor extends Actor {

  private[this] val deque: util.ArrayDeque[CircuitBreakerStats] = new util.ArrayDeque()

  override def postStop(): Unit = {
    println("Circtuit breaker stats actor was stopped")
  }
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
      sender() ! HystrixDashboardEvent(listBuffer.toList)
  }
}
