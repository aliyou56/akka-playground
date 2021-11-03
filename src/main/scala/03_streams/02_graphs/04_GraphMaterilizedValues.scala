package streams.graphs

import scala.concurrent.Future
import scala.util.{ Failure, Success }

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ FlowShape, Materializer, SinkShape }

object GraphMaterializedValue extends App {

  implicit val system       = ActorSystem("GraphMaterializedValue")
  implicit val materializer = Materializer(system)

  val wordSource = Source(List("Akka", "is", "awesome", "rock", "the", "jvm"))
  val printer    = Sink.foreach[String](println)
  val counter    = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
    A composite component (sink)
    - prints out all strings which are lowercase
    - counts the strings that are short (< 5 chars)
   */
  val complexWordSink: Sink[String, Future[Int]] = Sink.fromGraph(
    GraphDSL.createGraph(printer, counter)((printerMatValue, counterMatValue) => counterMatValue) {
      implicit builder => (printerShape, counterShape) =>
        import GraphDSL.Implicits._

        // ste 2 - SHAPES
        val broadcast         = builder.add(Broadcast[String](2))
        val lowercaseFiler    = builder.add(Flow[String].filter(word => word == word.toLowerCase))
        val shortStringFilter = builder.add(Flow[String].filter(_.length < 5))

        // step 3 - connections
        broadcast ~> lowercaseFiler ~> printerShape
        broadcast ~> shortStringFilter ~> counterShape

        // ste 4 - the shape
        SinkShape(broadcast.in)
    }
  )

  import system.dispatcher
  val shortStringCountFuture =
    wordSource
      .toMat(complexWordSink)(Keep.right)
      .run()

  shortStringCountFuture.onComplete {
    case Success(count)     => println(s"The total number of short strings is: $count")
    case Failure(exception) => println(s"The count of short strings failed: $exception")
  }

  /** Exercise
    */
  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink: Sink[B, Future[Int]] = Sink.fold[Int, B](0)((count, _) => count + 1)

    Flow.fromGraph(
      GraphDSL.createGraph(counterSink) { implicit builder => counterSinkShape =>
        import GraphDSL.Implicits._

        val broadcast         = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)

        originalFlowShape ~> broadcast ~> counterSinkShape

        FlowShape(originalFlowShape.in, broadcast.out(1))
      }
    )
  }

  val simpleSource = Source(1 to 42)
  val simpleFlow   = Flow[Int].map(identity)
  val simpleSink   = Sink.ignore

  val enhancedFlowCountFuture =
    simpleSource
      .viaMat(enhanceFlow(simpleFlow))(Keep.right)
      .toMat(simpleSink)(Keep.left)
      .run()

  enhancedFlowCountFuture.onComplete {
    case Success(count) => println(s"$count elements went through the enhanced flow")
    case Failure(ex)    => println(s"Error: $ex")
  }
}
