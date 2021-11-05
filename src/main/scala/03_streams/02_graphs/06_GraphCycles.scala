package streams.graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ ClosedShape, Materializer, OverflowStrategy, UniformFanInShape }

/** Cycles risk deadlocking
  *   - add bounds to the number of elements in the cycle
  *
  * trad offs
  *   boundedness vs liveness
  */
object GraphCycles extends App {

  implicit val system       = ActorSystem("GraphCycles")
  implicit val materializer = Materializer(system)

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape  = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    // format: off
    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape
    // format: on

    ClosedShape
  }

  // graph cycle deadlock!
  RunnableGraph
    .fromGraph(accelerator)
  // .run()

  /** Solution 1: MergePreferred */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape  = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  RunnableGraph
    .fromGraph(actualAccelerator)
  // .run()

  /** Solution 2: buffers */
  val bufferRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape  = builder.add(MergePreferred[Int](1))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape.preferred <~ repeaterShape

    ClosedShape
  }

  RunnableGraph.fromGraph(bufferRepeater)
  // .run()

  /** Challenge: create a fan-in shape
    *   - two inputs which will be fed with Exactly ONE number
    *   - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
    */
  val fibonacciGenerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zip            = builder.add(Zip[BigInt, BigInt]())
    val mergePreferred = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fiboLogic = builder.add(Flow[(BigInt, BigInt)].map {
      case (last, prev) =>
        Thread.sleep(100)
        (last + prev, last)
    })
    val broadcast   = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))

    // format: off
    zip.out ~>  mergePreferred    ~>     fiboLogic ~> broadcast ~> extractLast
                mergePreferred.preferred     <~       broadcast
    // format: on

    UniformFanInShape(extractLast.out, zip.in0, zip.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = builder.add(Source.single[BigInt](1))
      val source2 = builder.add(Source.single[BigInt](1))
      val sink    = builder.add(Sink.foreach[BigInt](println))
      val fibo    = builder.add(fibonacciGenerator)

      source1 ~> fibo.in(0)
      source2 ~> fibo.in(1)
      fibo.out ~> sink

      ClosedShape
    }
  )

  fiboGraph.run()
}
