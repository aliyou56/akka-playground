package streams.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ ClosedShape, Materializer }

/** Non-linear components:
  *   - fan-out (Broadcast, Balance)
  *   - fan-in (Zip, ZipWith, Concat)
  */
object GraphBasics extends App {

  implicit val system       = ActorSystem("GraphBasics")
  implicit val materializer = Materializer(system)

  val input       = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1) // hard computation
  val multiplier  = Flow[Int].map(_ * 10)
  val output      = Sink.foreach[(Int, Int)](println)

  // step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // step 2 - add the necessary components of this graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip       = builder.add(Zip[Int, Int]())   // fan-out operator

      // step 3 tying up the components
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output

      // step 4 - return a closed Shape
      ClosedShape
    } // graph
  )   // runnable graph

  // graph.run()

  /** Exercise 1: feed a source info into 2 sinks at the same time */

  val firstSink  = Sink.foreach[Int](x => println(s"First sink: $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second sink: $x"))

  val SourceToTwoSinks = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      // format: off
      input ~> broadcast ~> firstSink // implicit port numbering
               broadcast ~> secondSink
      // format: on

      ClosedShape
    }
  )

  /** Exercise 2: balanced */

  import scala.concurrent.duration._

  val fastSource = input.throttle(5, 1.second)
  val slowSource = input.throttle(2, 1.second)

  val sink1 = Sink.fold[Int, Int](0) { (count, _) =>
    println(s"Sink 1 number of elements: $count")
    count + 1
  }
  val sink2 = Sink.fold[Int, Int](0) { (count, _) =>
    println(s"Sink 2 number of elements: $count")
    count + 1
  }

  val balancedGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val merge   = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      // format: off
      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge 
      balance ~> sink2 
      // format: on

      ClosedShape
    }
  )

  balancedGraph.run()
}
