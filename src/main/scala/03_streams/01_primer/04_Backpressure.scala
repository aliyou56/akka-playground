package streams.primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy }

/** data flows through streams in response to demand
  *
  * overflow strategies:
  *  - drop head = oldest
  *  - drop tail = newest
  *  - drop new = exact element to be added = keeps the buffer
  *  - drop the entire buffer
  *  - backpressure signal
  *  - fail
  */
object Backpressure extends App {

  implicit val system       = ActorSystem("BackpressureBasics")
  implicit val materializer = Materializer(system)

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  fastSource
    .to(slowSink)
  // .run() // not backpressure (fusion)

  fastSource
    .async
    .to(slowSink)
  // .run() // backpressure

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  fastSource
    .async
    .via(simpleFlow)
    .async
    .to(slowSink)
  //  .run()

  /*
  reactions to backpressure (in order):
    - try to slow down if possible
    - buffer elements until there's more demand
    - drop down elements from the buffer if it overflows
    - tear down/kill the whole stream (failure)
   */

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)

  fastSource
    .async
    .via(bufferedFlow)
    .async
    .to(slowSink)
  // .run()

  // throttling
  import scala.concurrent.duration._
  fastSource
    .throttle(2, 1.second) // at most 2 elements per second
    .runWith(Sink.foreach(println))
}
