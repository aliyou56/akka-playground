package streams.advanced

import scala.util.Random

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, Inlet, Materializer, Outlet, SinkShape, SourceShape }

object CustomOperators extends App {

  implicit val system       = ActorSystem("CustomOperators")
  implicit val materializer = Materializer(system)

  // 1. a custom source which emits random numbers until cancelled

  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {

    val outPort = Outlet[Int]("randomGenerator")
    val random  = new Random()

    override def shape: SourceShape[Int] = SourceShape(outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        setHandler(
          outPort,
          new OutHandler {
            // when there is demand from downstream
            override def onPull(): Unit = {
              // emit a new element
              val nextNumber = random.nextInt(max)
              // push it out of the outPort
              push(outPort, nextNumber)
            }
          },
        )
      }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  // randomGeneratorSource.runWith(Sink.foreach(println))

  // 2. a custom sink that prints elements in batches of a given size
  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {
    val inPort = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape(inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        override def preStart(): Unit =
          pull(inPort)

        // mutable state
        val batch = new collection.mutable.Queue[Int]

        def dequeueAll = batch.dequeueAll(_ => true).mkString("[", ",", "]")

        setHandler(
          inPort,
          new InHandler {
            // when the upstream wants to send me an element
            override def onPush(): Unit = {
              val nextElement = grab(inPort)
              batch.enqueue(nextElement)

              // assume some complex computation
              Thread.sleep(100)

              if (batch.size >= batchSize) println(s"New batch: $dequeueAll")

              pull(inPort) // send demand upstream
            }

            override def onUpstreamFinish(): Unit =
              if (batch.nonEmpty) println(s"New batch: $dequeueAll \n Stream finished")
          },
        )
      }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))
  randomGeneratorSource
    .to(batcherSink)
    .run()
}
