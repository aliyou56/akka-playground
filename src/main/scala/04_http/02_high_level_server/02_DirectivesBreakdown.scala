package http.high_level_server

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, StatusCodes }
import akka.stream.Materializer

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

  /**
   * 3. Composite directives
   */
  val simpleNestedRoute =
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndpoint") & extractRequest & extractLog) { (request, log) =>
      log.info(s"http request: $request")
      complete(StatusCodes.OK)
    }

  // grouping: /about and /aboutUs
  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
      path("aboutUs") {
        complete(StatusCodes.OK)
      }

  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  // blog.com/42 AND blog.com/postId=42
  val blogById =
    path(IntNumber) { blogPostId =>
      // complex server logic
      complete(StatusCodes.OK)
    }

  val blogByQueryParam =
    parameter(Symbol("postID").as[Int]) { blogPostId =>
      // Same complex server logic
      complete(StatusCodes.OK)
    }

  val combinedBlogById =
    (path(IntNumber) | parameter(Symbol("postId").as[Int])) { blogPostId =>
      complete(StatusCodes.OK)
    }

  /**
   * 4. "actionnable" directives
   */

  val completOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notSupport") {
      failWith(new RuntimeException("Unsupported")) // completes with HTTP 500
    }

  val routeWithRejection =
    path("home") {
      reject
    } ~
      path("index") {
        completOkRoute
      }

  /** Exercise */

  val getOrPath =
    path("api" / "endpoint") {
      get {
        completOkRoute
      } ~
        post {
          complete(StatusCodes.Forbidden)
        }
    }

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(getOrPath)
}
