package talos.laws

import org.scalacheck._
import talos.circuitbreakers.EventBus
import talos.events.TalosEvents.model.CircuitBreakerEvent
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.matchers.should.Matchers

trait EventBusLaws[S] extends Matchers{
  def acceptMsg: CircuitBreakerEvent

  implicit def eventBus: EventBus[S]



  private[laws] def canConsumeMessagesPublishedToTheEventBus = {

    val circuitBreakerEvent = implicitly[Arbitrary[CircuitBreakerEvent]]

    circuitBreakerEvent.arbitrary.sample match {
      case Some(event) =>
        eventBus publish event
        acceptMsg shouldBe event
      case None =>
        fail("Event bus events seem broken: could not autogenerate circuit breaker event")
    }

  }
}
