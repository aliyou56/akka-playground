package http.client

import scala.concurrent.duration._

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import spray.json._

final case class CreditCard(
  serialNumber: String,
  securityCode: String,
  account: String
)

object PaymentSystemDomain {
  final case class PaymentRequest(
    creditCard: CreditCard,
    receiverAccount: String,
    amount: BigDecimal
  )
  case object PaymentAccepted
  case object PaymentRejected
}

trait PaymentJsonProtocol extends DefaultJsonProtocol {
  implicit val creditCardFormat     = jsonFormat3(CreditCard)
  implicit val paymentRequestFormat = jsonFormat3(PaymentSystemDomain.PaymentRequest)
}

class PaymentValidator extends Actor with ActorLogging {
  import PaymentSystemDomain._

  override def receive: Receive = {
    case PaymentRequest(CreditCard(serialNumber, _, senderAccount), receiverAccount, amount) =>
      log.info(s"$senderAccount is trying to send $amount dollars to $receiverAccount")
      if (serialNumber == "4242-4242-4242-4242") sender() ! PaymentAccepted
      else sender() ! PaymentRejected
  }
}

object PaymentSystem extends App with PaymentJsonProtocol with SprayJsonSupport {

  // microservice for payments
  implicit val system       = ActorSystem("PaymentSystem")
  implicit val materializer = Materializer(system)
  import system.dispatcher

  import PaymentSystemDomain._

  val paymentValidator = system.actorOf(Props[PaymentValidator](), "paymentValidator")

  implicit val timeout = Timeout(2.seconds)

  val paymentRoute =
    path("api" / "payments") {
      post {
        entity(as[PaymentRequest]) { paymentRequest =>
          complete(
            (paymentValidator ? paymentRequest).map {
              case PaymentRejected => StatusCodes.Forbidden
              case PaymentAccepted => StatusCodes.OK
              case _               => StatusCodes.BadRequest
            }
          )
        }
      }
    }

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(paymentRoute)
}
