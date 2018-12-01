package talos.laws

import org.scalacheck._
import org.scalatest.Matchers
import talos.circuitbreakers.EventBus
import talos.events.TalosEvents.model.CircuitBreakerEvent
import org.scalacheck.ScalacheckShapeless._

trait EventBusLaws[S] extends Matchers{
  def acceptMsg: CircuitBreakerEvent

  implicit def eventBus: EventBus[S]



  private[laws] def canConsumeMessagesPublishedToTheEventBus = {

    val circuitBreakerEvent = implicitly[Arbitrary[CircuitBreakerEvent]]

    val successfulCall = circuitBreakerEvent.arbitrary.sample.get

    eventBus publish successfulCall

    acceptMsg shouldBe successfulCall
  }
}
