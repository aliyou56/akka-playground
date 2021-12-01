package http.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, StatusCodes }
import akka.stream.Materializer
import akka.event.LoggingAdapter

object DirectivesBreakdown extends App {

  implicit val system       = ActorSystem("DirectivesBreakdown")
  implicit val materializer = Materializer(system)

  import akka.http.scaladsl.server.Directives._

  /**
   * 1. Filtering directives
   */
  val simpleHttpMethodRoute =
    post { // equivalent directives for get, put, patch, delete, head, options
      complete(StatusCodes.Forbidden)
    }

  val pathRoute =
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`application/json`,
          """
            <html>
              <body>
                Hello form About page
              </body>
            </html>
          """.stripMargin,
        )
      )
    }

  val complexRoute =
    path("api" / "endpoint") {
      // not the same as "api/endpoint" => url encoding (/ = %2F)
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash { // :8080 or 8:080/
      complete(StatusCodes.OK)
    }

  /**
   * 2. Extraction directives
   */

  // GET on /api/item/42
  val pathExtractionRoute =
    path("api" / "item" / IntNumber) { (itemNumber: Int) =>
      println(s"Got a number in the path: $itemNumber")
      complete(StatusCodes.OK)
    }

  val pathMultiExtractionRoute =
    path("api" / "order" / IntNumber / IntNumber) { (id, inventory) =>
      println(s"Got 2 numbers in the path: $id, $inventory")
      complete(StatusCodes.OK)
    }

  val queryParamExtractionRoute =
    // /api/item/id=45
    path("api" / "item") {
      parameter(Symbol("id").as[Int]) { (id: Int) =>
        println(s"Id: $id")
        complete(StatusCodes.OK)
      }
    }

  val extractRequestRoute =
    path("controlEndpoint") {
      extractRequest { (httpRequest: HttpRequest) =>
        extractLog { (log: LoggingAdapter) =>
          log.info(s"http request: $httpRequest")
          complete(StatusCodes.OK)
        }
      }
    }

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(complexRoute)
}
