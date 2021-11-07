package streams.techniques

import scala.concurrent.duration._

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy }
import akka.util.Timeout

object IntegratingWithActors extends App {

  implicit val system       = ActorSystem("IntegratingWithActors")
  implicit val materializer = Materializer(system)

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just receive a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just receive a number: $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  val simpleActor  = system.actorOf(Props[SimpleActor](), "simpleActor")
  val numberSource = Source(1 to 10)

  // actor as a flow
  implicit val timeout = Timeout(2.seconds)
  val actorBaseFlow    = Flow[Int].ask[Int](parallelism = 4)(simpleActor)
  val simpleSink       = Sink.foreach[Int](println)

  numberSource
    .via(actorBaseFlow)
    .to(Sink.foreach[Int](println))
  // .run()

  numberSource
    .ask[Int](parallelism = 4)(simpleActor)
    .to(simpleSink)
  // .run()

  /** Actor as a source */
  val actorPoweredSource: Source[Int, ActorRef] =
    Source.actorRef[Int](bufferSize = 10, OverflowStrategy.dropHead)

  val materializedActorRef: ActorRef =
    actorPoweredSource
      .to(
        Sink.foreach[Int](n => println(s"Actor powered flow get number: $n"))
      )
      .run()

  materializedActorRef ! 10

  // terminating the system
  materializedActorRef ! akka.actor.Status.Success("complete")

  /** Actor as a destination/sink *
    *   - an init message
    *   - an ack message to confirm the reception
    *   - a complete message
    *   - a function to generate a message in case the stream throws an exception
    */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")
      case message =>
        log.info(s"Message $message has come to its final resting point.")
        sender() ! StreamAck
    }
  }
  val destinationActor = system.actorOf(Props[DestinationActor](), "destinationActor")

  val actorPoweredSink = Sink.actorRefWithAck[Int](
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    onFailureMessage = throwable => StreamFail(throwable), // optional
  )

  Source(1 to 10)
    .to(actorPoweredSink)
    .run()

  // Sink.actorRef() not recommended, unable to backpressure
}
