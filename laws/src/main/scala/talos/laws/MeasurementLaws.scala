package talos.laws

import cats.effect._
import cats.implicits._
import talos.events.TalosEvents.model.{CallFailure, CallTimeout, SuccessfulCall}

import scala.concurrent.{ExecutionContext, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Failure, Try}

trait MeasurementLaws[S, C, F[_]] extends EventBusLaws[S] with CircuitBreakerSpec[C, F] {
  implicit val timer = IO.timer(ExecutionContext.global)



  private[laws] def measuresElapsedTimeInSuccessfulCalls(implicit F: Effect[F]) = {
   run(F.liftIO(IO.sleep(1 second)))
    val event = acceptMsg.asInstanceOf[SuccessfulCall]
    event.copy(elapsedTime = event.elapsedTime.toSeconds seconds) shouldBe
      SuccessfulCall(talosCircuitBreaker.name, 1 second)
  }

  private[laws] def measuresElapsedTimeInFailedCalls(implicit F: Effect[F]) = {
    val unsafeCall = IO.sleep(1 second) <* IO.raiseError(new RuntimeException)

    Try(run(F.liftIO(unsafeCall)))

    val event = acceptMsg.asInstanceOf[CallFailure]
    event.copy(elapsedTime = event.elapsedTime.toSeconds seconds)  shouldBe
      CallFailure(talosCircuitBreaker.name, 1 second)

  }

  private[laws] def measuresElapsedTimeInTimeouts(implicit F: Effect[F]) = {
    Try(run(F.liftIO(IO.sleep(callTimeout + (1 second))))) should matchPattern {
      case Failure(a) if a.isInstanceOf[TimeoutException] =>
    }
    val event = acceptMsg.asInstanceOf[CallTimeout]
    event.copy(elapsedTime = event.elapsedTime.toSeconds seconds) shouldBe
      CallTimeout(talosCircuitBreaker.name, callTimeout)
  }


}
