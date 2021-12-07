package http.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{
  MethodRejection,
  MissingQueryParamRejection,
  Rejection,
  RejectionHandler,
  Route
}
import akka.stream.Materializer

/**
 * reject => pass the request to another branch in the routing tree
 * a rejection is Not a failure
 * rejections are aggregated
 */
object HandlingRejections extends App {

  implicit val system       = ActorSystem("HandlingRejections")
  implicit val materializer = Materializer(system)

  val simpleRoute: Route =
    path("api" / "endpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        parameter(Symbol("id")) { _ =>
          complete(StatusCodes.OK)
        }
    }

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers: Route =
    handleRejections(badRequestHandler) { // handle rejections from the top level
      // define server logic
      path("api" / "endpoint") {
        get {
          complete(StatusCodes.OK)
        }
      } ~
        post {
          handleRejections(forbiddenHandler) { // handle rejections within
            parameter(Symbol("param")) { _ =>
              complete(StatusCodes.OK)
            }
          }
        }
    }

  // RejectionHandler.default by default

  implicit val customRejectHandler = RejectionHandler
    .newBuilder()
    .handle {
      case m: MethodRejection =>
        println(s"Got a method rejection: $m")
        complete("Rejected method!")
    }
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"Got a query param rejection: $m")
        complete("Rejected query param!")
    }
    .result()

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(simpleRoute)
}
