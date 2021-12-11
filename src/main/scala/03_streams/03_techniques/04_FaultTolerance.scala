package streams.techniques

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ RestartSource, Sink, Source }
import akka.stream.{ ActorAttributes, Materializer, Supervision }
import akka.stream.RestartSettings

/** Logging
  * Recovering
  * Backoff supervision
  * Supervision strategies
  */
object FaultTolerance extends App {

  implicit val system: ActorSystem        = ActorSystem("FaultTolerance")
  implicit val materializer: Materializer = Materializer(system)

  // 1. logging
  val faultySource =
    Source(1 to 10)
      .map(e => if (e == 6) throw new RuntimeException else e)

  faultySource
    .log("trackingElement")
    .to(Sink.ignore)
  // .run()

  // 2. gracefully terminating a stream
  faultySource
    .recover { case _: RuntimeException => Int.MinValue }
    .log("gracefulSource")
    .to(Sink.ignore)
  // .run()

  // 3. recover with another stream
  faultySource
    .recoverWithRetries(3, { case _: RuntimeException => Source(90 to 99) })
    .log("recoverWithRetries")
    .to(Sink.ignore)
  // .run()

  // 4. backoff supervision
  val restartSource =
    RestartSource
      .onFailuresWithBackoff(
        RestartSettings(
          minBackoff = 1.second,
          maxBackoff = 30.seconds,
          randomFactor = 0.2,
        )
      ) { () =>
        val randomNumber = new Random().nextInt(20)
        Source(1 to 10)
          .map(e => if (e == randomNumber) throw new RuntimeException else e)
      }

  restartSource
    .log("restartBackoff")
    .to(Sink.ignore)
  // .run()

  // 5. supervision strategy
  val numbers =
    Source(1 to 20)
      .map { n =>
        if (n == 13) throw new RuntimeException else n
      }

  val supervisedNumbers =
    numbers
      .withAttributes(ActorAttributes.supervisionStrategy {
        /*
          Resume: skips the faulty element
          Stop: stop the stream
          Restart: Resume + clears internal state (ex: fold - accumulation)
         */
        case _: RuntimeException => Supervision.Resume
        case _                   => Supervision.Stop
      })

  supervisedNumbers
    .to(Sink.ignore)
    .run()
}
