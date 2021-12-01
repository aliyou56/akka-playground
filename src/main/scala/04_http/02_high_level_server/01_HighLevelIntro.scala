package http.high_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.Materializer

/**
 * type Route = RequestContext => Future[RouteResult]
 * RequestContext contains:
 *      the HttpRequest being handled
 *      the actor system
 *      the actor materializer
 *      the logging adapter
 *      routing settings
 *
 *    Directives create Routes; composing routes create a routing tree
 *      filtering and nesting
 *      chaining with ~
 *      extracting data
 */
object HighLevelIntro extends App {

  implicit val system       = ActorSystem("HighLevelIntro")
  implicit val materializer = Materializer(system)

  // directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") {             // Directive
      complete(StatusCodes.OK) // Directive
    }

  val pathGetRoute: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

  // chaining directives
  val chainedRoute: Route =
    path("api") {
      get {
        complete(StatusCodes.OK)
      } ~
        post {
          complete(StatusCodes.Forbidden)
        }
    } ~
      path("home") {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
          <html>
            <body>
              Hello from Akka Http
            </body>
          </html>
          """.stripMargin,
          )
        )
      } // Routing tree

  Http()
    .newServerAt(interface = "localhost", port = 8080)
    // .enableHttps(ConnectionContext.httpsServer(SSLContext.getDefault()))
    .bindFlow(chainedRoute)
}
