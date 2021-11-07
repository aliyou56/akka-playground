package streams.techniques

import java.util.Date

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy }

object AdvancedBackpressure extends App {

  implicit val system       = ActorSystem("AdvancedBackpressure")
  implicit val materializer = Materializer(system)

  // controlling backpressure
  val controlFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery failed", new Date),
    PagerEvent("Illegal elements in the datapipeline", new Date),
    PagerEvent("Number of HTTP 500 spiked", new Date),
    PagerEvent("A service stopped responding", new Date),
  )
  val eventSource = Source(events)

  val onCallEngineer = "engineer@company.com" // a fast service for fetching oncall emails

  def sendEmail(notification: Notification) =
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}")

  val notificationSink =
    Flow[PagerEvent]
      .map(event => Notification(onCallEngineer, event))
      .to(Sink.foreach[Notification](sendEmail))

  eventSource
    .to(notificationSink)
  // .run()

  /** un-backpressure source
    */

  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    sendEmail(notification)
  }

  // alternative to backpressure
  // conflate never backpressure
  def aggregateNotification =
    Flow[PagerEvent]
      .conflate { (event1, event2) =>
        val nInstances = event1.nInstances + event2.nInstances
        PagerEvent(s"You have $nInstances events that require your attention", new Date, nInstances)
      }
      .map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  eventSource
    .via(aggregateNotification)
    .async
    .to(Sink.foreach[Notification](sendEmailSlow))
  // .run()

  /** Slow producers: extrapolate/expand
    */
  val slowCounter = Source(LazyList.from(1)).throttle(1, 1.second)
  val hungrySink  = Sink.foreach[Int](println)

  // create the iterator only when there is an unmeet demand
  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater     = Flow[Int].extrapolate(element => Iterator.continually(element))

  // create the iterator at all time
  val expander = Flow[Int].expand(element => Iterator.continually(element))

  slowCounter
    .via(extrapolator)
    .to(hungrySink)
  // .run()

}
