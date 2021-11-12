package streams.advanced

import scala.util.Random

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, Inlet, Materializer, Outlet, SinkShape, SourceShape }
import akka.stream.FlowShape
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageWithMaterializedValue
import scala.concurrent.Future
import scala.concurrent.Promise
import akka.stream.scaladsl.Keep
import scala.util.Success
import scala.util.Failure

/** Handler callbacks never called concurrently (onPush)
  * DO NOT expose mutable state outside these handlers!
  */
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
  // .run()

  /** Exercise: a custom flow - a simple filter flow
    */
  class SimpleFilter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort  = Inlet[T]("inPort")
    val outPort = Outlet[T]("outPort")

    override val shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        setHandler(
          inPort,
          new InHandler {
            override def onPush(): Unit =
              try {
                val nextElement = grab(inPort)

                if (predicate(nextElement)) push(outPort, nextElement)
                else pull(inPort) // ask for another element
              }
              catch {
                case e: Throwable => failStage(e)
              }
          },
        )

        setHandler(
          outPort,
          new OutHandler {
            override def onPull(): Unit = pull(inPort)
          },
        )
      }
  }

  val simpleFilter = Flow.fromGraph(new SimpleFilter[Int](_ > 50))

  randomGeneratorSource
    .via(simpleFilter)
    .to(batcherSink)
  // .run()

  /** Materialized values in graph stages
    *
    * 3. a flow that counts the number of elements that go through it
    */

  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {

    val inPort  = Inlet[T]("counterIn")
    val outPort = Outlet[T]("counterOut")

    override val shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {

      val promise = Promise[Int]()
      val logic = new GraphStageLogic(shape) {
        // setting mutable state
        var counter = 0

        setHandler(
          outPort,
          new OutHandler {
            override def onPull(): Unit = pull(inPort)
            override def onDownstreamFinish(cause: Throwable): Unit = {
              promise.success(counter)
              super.onDownstreamFinish(cause)
            }
          },
        )

        setHandler(
          inPort,
          new InHandler {
            override def onPush(): Unit = {
              val nextElement = grab(inPort)
              counter += 1
              push(outPort, nextElement)
            }

            override def onUpstreamFinish(): Unit = {
              promise.success(counter)
              super.onUpstreamFinish()
            }

            override def onUpstreamFailure(ex: Throwable): Unit = {
              promise.failure(ex)
              super.onUpstreamFailure(ex)
            }
          },
        )
      }

      logic -> promise.future
    }
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  import system.dispatcher
  Source(1 to 10)
    // .map(x => if (x == 7) throw new RuntimeException("Boom !!!") else x)
    .viaMat(counterFlow)(Keep.right)
    .to(Sink.foreach(x => if (x == 7) throw new RuntimeException("Boom Sink !!!") else println(x)))
    // .to(Sink.foreach(println))
    .run()
    .onComplete {
      case Success(count) => println(s"The number of elements passed: $count")
      case Failure(ex)    => println(s"counting the elements failed: $ex")
    }
}
