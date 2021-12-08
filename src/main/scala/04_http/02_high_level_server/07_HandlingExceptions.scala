package http.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ ExceptionHandler, Route }
import akka.stream.Materializer

object HandlingExceptions extends App {

  implicit val system       = ActorSystem("HandlingExceptions")
  implicit val materializer = Materializer(system)

  val simpleRoute: Route =
    path("api" / "people") {
      get {
        // directive that throws some exception
        throw new RuntimeException("Getting all the people took too long")
      } ~
        post {
          parameter(Symbol("id")) { id =>
            if (id.length > 2)
              throw new NoSuchElementException(s"Parameter $id cannot be found in the DB")
            else
              complete(StatusCodes.OK)
          }
        }
    }

  // implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
  //   case e: RuntimeException =>
  //     complete(StatusCodes.NotFound, e.getMessage)
  // case e: IllegalArgumentException =>
  //   complete(StatusCodes.BadRequest, e.getMessage)
  // }

  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: NoSuchElementException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val dedicatedHandleRoute =
    handleExceptions(runtimeExceptionHandler) {
      path("api" / "people") {
        get {
          // directive that throws some exception
          throw new RuntimeException("Getting all the people took too long")
        } ~
          handleExceptions(noSuchElementExceptionHandler) {
            post {
              parameter(Symbol("id")) { id =>
                if (id.length > 2)
                  throw new NoSuchElementException(s"Parameter $id cannot be found in the DB")
                else
                  complete(StatusCodes.OK)
              }
            }
          }
      }
    }

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(dedicatedHandleRoute)
}
