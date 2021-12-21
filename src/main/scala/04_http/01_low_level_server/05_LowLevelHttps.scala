package http.low_level_server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import akka.stream.Materializer
import http.utils.HttpsContext

object LowLevelHttps extends App {

  implicit val system       = ActorSystem("LowLevelHttps")
  implicit val materializer = Materializer(system)

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            <html>
              <body>
                HEllo from Akka HTTP !
              </body>
            </html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            <html>
              <body>
                cOOPS! The resource can't be found.
              </body>
            </html>
          """.stripMargin
        )
      )
  }

  Http()
    .newServerAt("localhost", 8443)
    .enableHttps(HttpsContext.httpsConnectionContext)
    .bindSync(requestHandler)
}
