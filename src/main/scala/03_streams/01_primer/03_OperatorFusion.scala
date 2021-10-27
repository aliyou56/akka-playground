package streams.primer

import akka.actor.{ Actor, ActorSystem, Props }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Sink, Source }

/** Akka stream components are fused = run on the same actor
  * Async boundaries
  *   . components run on different actors
  *   . better throughput
  */
object OperatorFusion extends App {

  implicit val system       = ActorSystem("OperatorFusion")
  implicit val materializer = Materializer(system)

  val simpleSource = Source(1 to 1000)
  val simpleFlow   = Flow[Int].map(_ + 1)
  val simpleFlow2  = Flow[Int].map(_ * 10)
  val simpleSink   = Sink.foreach[Int](println)

  // this run on the same actor -> operator/component FUSION
  simpleSource
    .via(simpleFlow)
    .via(simpleFlow2)
    .to(simpleSink)
  // .run()s

  // "equivalent" behavior
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        // flow operations
        val x2 = x + 1
        val y  = x2 * 1
        println(y) // sink operation
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor]())
  // (1 to 1000).foreach(simpleActor ! _)

  // complex flow:
  val complexFlow = Flow[Int].map { x =>
    Thread.sleep(1000) // simulating a long computation
    x + 10
  }
  val complexFlow2 = Flow[Int].map { x =>
    Thread.sleep(1000) // simulating a long computation
    x * 10
  }

  simpleSource
    .via(complexFlow)
    .via(complexFlow2)
    .to(simpleSink)
  // .run()

  // async boundary
  simpleSource
    .via(complexFlow)
    .async // runs on one actor
    .via(complexFlow2)
    .async // runs on another actor
    .to(simpleSink)
  //  .run()

  // ordering guarantees
  Source(1 to 5)
    .map { e => println(s"Flow A: $e"); e }
    .async
    .map { e => println(s"Flow B: $e"); e }
    .async
    .map { e => println(s"Flow C: $e"); e }
    .async
    .runWith(Sink.ignore)
}
