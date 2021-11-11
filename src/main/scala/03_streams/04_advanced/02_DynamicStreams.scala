package streams.advanced

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ BroadcastHub, Keep, MergeHub, Sink, Source }
import akka.stream.{ FlowShape, Graph, KillSwitches, Materializer, UniqueKillSwitch }
import akka.{ Done, NotUsed }

/** Stop or abort a stream at runtime
  * Dynamically add fan-in/fan-out branches
  */
object DynamicStreams extends App {

  implicit val system       = ActorSystem("DynamicStreams")
  implicit val materializer = Materializer(system)

  // 1. Kill switch
  val killSwitchFlow: Graph[FlowShape[Int, Int], UniqueKillSwitch] = KillSwitches.single[Int]

  val counter: Source[Int, NotUsed] = Source(LazyList.from(1)).throttle(1, 1.second).log("counter")
  val sink: Sink[Any, Future[Done]] = Sink.ignore

  val killSwitch =
    counter
      .viaMat(killSwitchFlow)(Keep.right)
      .to(sink)
  //     .run()

  // system.scheduler.scheduleOnce(3.seconds) {
  //   killSwitch.shutdown()
  // }

  val anotherCounter   = Source(LazyList.from(1)).throttle(2, 1.second).log("counter2")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

  // counter.via(sharedKillSwitch.flow).runWith(sink)
  // anotherCounter.via(sharedKillSwitch.flow).runWith(sink)

  // system.scheduler.scheduleOnce(3.seconds) {
  //   sharedKillSwitch.shutdown()
  // }

  // MergeHub
  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  val materializedSink =
    dynamicMerge
      .to(Sink.foreach[Int](println))
  // .run()

  // we can use this sink any time we like
  // Source(1 to 10).runWith(materializedSink)
  // counter.runWith(materializedSink)

  // BroadcastHub
  val dynamicBroadcast: Sink[Int, Source[Int, NotUsed]] = BroadcastHub.sink[Int]
  val materializedSource =
    Source(1 to 100)
  // .runWith(dynamicBroadcast)

  // materializedSource.runWith(Sink.ignore)
  // materializedSource.runWith(Sink.foreach[Int](println))

  /** Challenge - combine a mergeHub and a broadcastHub.
    * A publisher-subscriber component
    */
  val mergeHub: Source[String, Sink[String, NotUsed]]     = MergeHub.source[String]
  val broadcastHub: Sink[String, Source[String, NotUsed]] = BroadcastHub.sink[String]

  val (publisherPort, subscriberPort) = mergeHub.toMat(broadcastHub)(Keep.both).run()

  subscriberPort.runWith(Sink.foreach(e => println(s"I received: $e")))
  subscriberPort.map(_.length).runWith(Sink.foreach(n => println(s"I got a number: $n")))

  Source(List("Akka", "is", "amazing")).runWith(publisherPort)
  Source(List("I", "love", "Scala")).runWith(publisherPort)
  Source.single("Streamssssssss").runWith(publisherPort)
}
