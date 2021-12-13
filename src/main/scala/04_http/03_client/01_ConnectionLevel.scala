package http.client

import scala.util.{ Failure, Success }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  Uri
}
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import spray.json._

/**
 * Best for: long-lived requests
 */
object ConnectionLevel extends App with PaymentJsonProtocol {

  implicit val system       = ActorSystem("ConnectionLevel")
  implicit val materializer = Materializer(system)
  import system.dispatcher

  val connectionFlow = Http().outgoingConnection("www.google.com")

  // !! materialize akka stream and start tcp connection every single time
  def oneOffRequest(request: HttpRequest): Unit =
    Source
      .single(request)
      .via(connectionFlow)
      .runWith(Sink.head)
      .onComplete {
        case Success(response) => println(s"Got successful response: $response")
        case Failure(ex)       => println(s"Sending the request failed: $ex")
      }

  // oneOffRequest(HttpRequest())

  /**
   * Small payment system
   */

  import PaymentSystemDomain._

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "ml-test-account"),
    CreditCard("1234-1234-1234-1234", "123", "ml-aliyou-account"),
    CreditCard("1234-4321-1234-4321", "321", "ml-david-account")
  )

  val paymentsRequest = creditCards.map { creditCard =>
    PaymentRequest(creditCard, "store-account", 99)
  }

  val serverHttpRequests = paymentsRequest.map { paymentRequest =>
    HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentsRequest.toJson.prettyPrint
      )
    )
  }

  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost", 8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()
}
