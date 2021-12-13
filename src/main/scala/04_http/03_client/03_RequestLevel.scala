package http.client

import scala.util.{ Failure, Success }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpMethods, HttpRequest }
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import spray.json._

/**
 * Freedom from managing anything
 *
 * Best for: low-volume, low-latency requests
 */
object RequestLevel extends App with PaymentJsonProtocol {

  implicit val system       = ActorSystem("RequestLevel")
  implicit val materializer = Materializer(system)
  import system.dispatcher

  def helloGoogle() =
    Http()
      .singleRequest(HttpRequest(uri = "http://www.google.com"))
      .onComplete {
        case Success(response) =>
          response.discardEntityBytes() // !! important to avoid connection leak
          println(s"The request was successful and returned: $response")
        case Failure(ex) => println(s"The request failed with: $ex")
      }

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
    HttpRequest(
      HttpMethods.POST,
      uri = "http://localhost:8080/api/payments",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentsRequest.toJson.prettyPrint
      )
    )
  }

  Source(serverHttpRequests)
    .mapAsyncUnordered(10) { request =>
      Http().singleRequest(request)
    }
    .runForeach(println)
}
