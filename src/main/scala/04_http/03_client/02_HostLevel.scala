package http.client

import java.util.UUID

import scala.util.{ Failure, Success, Try }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Sink, Source }

import spray.json._

/**
 * Host-level API Benefits:
 * - freedom for managing individual connections
 * - ability to attach data to requests (aside from payloads)
 *
 * Best for: High volume and low-latency requests
 */
object HostLevel extends App with PaymentJsonProtocol {

  implicit val system       = ActorSystem("HostLevel")
  implicit val materializer = Materializer(system)

  val poolFlow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Http.HostConnectionPool] =
    Http().cachedHostConnectionPool[Int]("www.google.com")

  def helloGoogle() =
    Source(1 to 10)
      .map(i => (HttpRequest(), i))
      .via(poolFlow)
      .map {
        case (Success(response), value) =>
          response.discardEntityBytes() // !! Very important
          s"request $value has received response: $response"
        case (Failure(ex), value) =>
          s"Request $value has failed: $ex"
      }
      .runWith(Sink.foreach[String](println))

  // helloGoogle()

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
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentsRequest.toJson.prettyPrint
        )
      ),
      UUID.randomUUID().toString
    )
  }

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach {
      case (Success(response @ HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        println(s"The order ID $orderId was not allowed to proceed: $response")
      case (Success(response), orderId) =>
        println(s"The order ID $orderId was successful and returned the response: $response")
      // do something with the orderId: dispatch it, send a notification to the customer, etc...
      case (Failure(ex), orderId) =>
        println(s"The order ID $orderId could not be completed: $ex")
    }

}
