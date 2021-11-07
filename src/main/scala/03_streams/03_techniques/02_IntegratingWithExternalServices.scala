package streams.techniques

import java.util.Date

import scala.concurrent.Future

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout

object IntegratingWithExternalServices extends App {

  implicit val system       = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer = Materializer(system)
  // import system.dispatcher // not recommended for mapAsync
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericService[A, B](element: A): Future[B] = ???

  // ex: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(
    List(
      PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
      PagerEvent("FastDatePipeline", "Illegal elements in the data pipeline", new Date),
      PagerEvent("AkkaInfra", "A service stopped responding", new Date),
      PagerEvent("SuperFrontend", "A button doesn't work", new Date),
    )
  )

  object PagerService {
    private val engineers = List("Aliyou", "David", "Theo")
    private val emails = Map(
      "Aliyou" -> "aliyou@company.com",
      "David"  -> "david@company.com",
      "Theo"   -> "theo@company.com",
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val idx           = (pagerEvent.date.toInstant().getEpochSecond() / (24 * 3600)) % engineers.length
      val engineer      = engineers(idx.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")

      Thread.sleep(1000)

      engineerEmail
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  // mapAsync -> guarantees the relative order of elements
  // mapAsyncUnordered
  val pagedEngineerEmails =
    infraEvents
      .mapAsync(parallelism = 4) { event =>
        PagerService.processEvent(event)
      }
  val pagedEmailsSink =
    Sink.foreach[String] { email =>
      println(s"Successfully sent notification to $email")
    }

  pagedEngineerEmails
    .to(pagedEmailsSink)
  // .run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Aliyou", "David", "Theo")
    private val emails = Map(
      "Aliyou" -> "aliyou@company.com",
      "David"  -> "david@company.com",
      "Theo"   -> "theo@company.com",
    )

    private def processEvent(pagerEvent: PagerEvent) = Future {
      val idx           = (pagerEvent.date.toInstant().getEpochSecond() / (24 * 3600)) % engineers.length
      val engineer      = engineers(idx.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      log.info(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")

      Thread.sleep(1000)

      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(3.seconds)
  val pagerActor       = system.actorOf(Props[PagerActor](), "pagerActor")
  val alternativePagedEngineerEmails =
    infraEvents
      .mapAsync(4) { event =>
        (pagerActor ? event).mapTo[String]
      }

  alternativePagedEngineerEmails
    .to(pagedEmailsSink)
    .run()
}
