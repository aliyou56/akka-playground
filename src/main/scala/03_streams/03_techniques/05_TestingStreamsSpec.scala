package streams.techniques

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.testkit.{ TestKit, TestProbe }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/** Unit testing Akka Streams
  *   - final results
  *   - integrating with test actors
  *   - streams Testkit
  */
class TestingStreamsSpec
    extends TestKit(ActorSystem("TestingStreamsSpec"))
       with AnyWordSpecLike
       with BeforeAndAfterAll
       with Matchers {

  implicit val materializer = Materializer(system)

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should {
    "satisfy basic assertions" in {
      val simpleSource = Source(1 to 10)
      val simpleSink   = Sink.fold(0)((a: Int, b: Int) => a + b)
      val sumFuture    = simpleSource.toMat(simpleSink)(Keep.right).run()

      Await.result(sumFuture, 2.seconds) mustBe 55
    }

    "integrating with test actors via materialized values" in {
      import akka.pattern.pipe
      import system.dispatcher

      val simpleSource = Source(1 to 10)
      val simpleSink   = Sink.fold(0)((a: Int, b: Int) => a + b)

      val probe = TestProbe()

      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)

      probe.expectMsg(55)
    }

    "integrating with a test-actor-based sink" in {
      val simpleSource    = Source(1 to 5)
      val flow            = Flow[Int].scan[Int](0)(_ + _) // 0, 1, 3, 6, 10, 15
      val streamUnderTest = simpleSource.via(flow)

      val probe     = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "completionMessage")

      streamUnderTest.to(probeSink).run()

      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }

    "integrating with Streams TestKit Sink" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)

      val testSink             = TestSink.probe[Int]
      val materializeTestValue = sourceUnderTest.runWith(testSink)

      materializeTestValue
        .request(5)
        .expectNext(2, 4, 6, 8, 10)
        .expectComplete()
    }

    "integrating with Streams TestKit Source" in {
      import system.dispatcher

      val sinkUnderTest = Sink.foreach[Int] {
        case 13 => new RuntimeException("bad luck!")
        case _  =>
      }

      val testSource   = TestSource.probe[Int]
      val materialized = testSource.toMat(sinkUnderTest)(Keep.both).run()

      val (testPublisher, resultFuture) = materialized

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()

      resultFuture.onComplete {
        case Success(_) => fail("the sink under test should have thrown an exception on 13")
        case Failure(_) => // ok
      }
    }

    "test flow with a test source AND a test sink" in {
      val flowUnderTest = Flow[Int].map(_ * 2)

      val testSource = TestSource.probe[Int]
      val testSink   = TestSink.probe[Int]

      val materialized = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()

      val (publisher, subscriber) = materialized

      publisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(42)
        .sendNext(99)
        .sendComplete()

      subscriber
        .request(4)
        .expectNext(2, 10, 84, 198)
        .expectComplete()
    }
  }

}
