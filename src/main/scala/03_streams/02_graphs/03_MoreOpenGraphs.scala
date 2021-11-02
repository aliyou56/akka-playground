package streams.graphs

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ ClosedShape, FanOutShape2, Materializer, UniformFanInShape }

object MoreOpenGraphs extends App {

  implicit val system       = ActorSystem("MoreOpenGraphs")
  implicit val materializer = Materializer(system)

  /*
    Example: Max3 operator
    - 3 inputs of type int
    - the maximum of the 3
   */
  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    max1.out ~> max2.in0

    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // SHAPES declaration
      val max3Shape = builder.add(max3StaticGraph)

      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)
      max3Shape.out ~> maxSink

      ClosedShape
    }
  )

  // max3RunnableGraph.run()

  // same for UniformFanOutShape

  /** Non-uniform fan out shape
    *
    * Processing bank transactions
    * Transaction is suspicious if amount > 10000
    *
    * Streams component for txn
    * - output1: let the transaction go through
    * - output2: suspicious txn ids
    */
  case class Transaction(
      id: String,
      source: String,
      recipient: String,
      amount: Int,
      date: Date,
    )

  val transactionSource = Source(
    List(
      Transaction("741852963", "Alou", "Ami", 100, new Date),
      Transaction("789456123", "David", "Yindi", 100000, new Date),
      Transaction("965135478", "Theo", "Melissa", 7000, new Date),
    )
  )

  val bankProcessor             = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  // step 1
  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // step 2 - SHAPES definition
    val broadcast           = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(_.amount > 10000))
    val txnIdExtractor      = builder.add(Flow[Transaction].map[String](_.id))

    // step 3 - tie SHAPES
    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor

    // step 4
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)

      // format: off
      transactionSource       ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankProcessor
      suspiciousTxnShape.out1 ~> suspiciousAnalysisService
    // format: on

      ClosedShape
    }
  )

  suspiciousTxnRunnableGraph.run()
}
