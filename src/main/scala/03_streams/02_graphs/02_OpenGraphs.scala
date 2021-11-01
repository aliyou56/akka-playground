package streams.graphs

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

object OpenGraphs extends App {

  implicit val system       = ActorSystem("OpenGraphs")
  implicit val materializer = Materializer(system)

  /** A composite source that concatenates 2 sources
    *   - emits ALL the elements from the first source
    *   - then ALL the elements from the second
    */

  val firstSource  = Source(1 to 10)
  val secondSource = Source(1 to 1000)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // components declaration
      val concat: UniformFanInShape[Int, Int] = builder.add(Concat[Int](2))

      // format: off
      firstSource  ~> concat
      secondSource ~> concat
      // format: on

      SourceShape(concat.out)
    }
  )

  sourceGraph
    .to(Sink.foreach(println))
  //  .run()

  /** Complex Sink */
  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  firstSource
    .to(sinkGraph)
  // .run()

  /** Challenge - complex Flow
    * Write a complex flow that's composed of two other flows
    *   - one that adds 1 to a number
    *   - one that does number * 10
    */

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // everything operates on SHAPES
      val incrementerShape: FlowShape[Int, Int] = builder.add(Flow[Int].map(_ + 1))
      val multiplierShape: FlowShape[Int, Int]  = builder.add(Flow[Int].map(_ * 10))

      incrementerShape ~> multiplierShape

      FlowShape(incrementerShape.in, multiplierShape.out)
    } // static graph
  )   // component

  firstSource
    .via(flowGraph)
    .to(Sink.foreach(println))
  // .run()

  /** Exercise: Flow from a sink and a source */
  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        val sourceShape = builder.add(source)
        val sinkShape   = builder.add(sink)

        FlowShape(sinkShape.in, sourceShape.out)
      }
    )

  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))
}
