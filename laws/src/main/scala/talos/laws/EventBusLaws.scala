package talos.laws

import talos.circuitbreakers.EventBus
import org.scalacheck._
import Gen._
import org.scalatest.Matchers
import talos.events.TalosEvents.model.{CircuitBreakerEvent, SuccessfulCall}

trait EventBusLaws[S] extends Matchers{
  def acceptMsg: CircuitBreakerEvent

  implicit def eventBus: EventBus[S]

  private def genSuccessfulCall: Gen[SuccessfulCall] = for {
    elapsedTime <- finiteDuration
    name <- alphaNumStr
  } yield SuccessfulCall(name, elapsedTime)

  private[laws] def canConsumeMessagesPublishedToTheEventBus = {
    val successfulCall = genSuccessfulCall.sample.get

    eventBus publish successfulCall

    acceptMsg shouldBe successfulCall
  }
}
