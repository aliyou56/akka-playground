package http.high_level_server

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.CompactByteString

object WebSockets extends App {

  implicit val system       = ActorSystem("WebSockets")
  implicit val materializer = Materializer(system)

  // Message: TextMessage vs BinaryMessage
  val textMessage   = TextMessage(Source.single("Hello via a text message"))
  val binaryMessage = BinaryMessage(Source.single(CompactByteString("hello via a binary message")))

  val html =
    """
      <html>
        <head>
          <script>
            var ws = new WebSocket("ws://localhost:8080/greeter");
            console.log("starting websocket..");

            ws.onmessage = (event) => {
              var newChild = document.createElement("div");
              newChild.innerText = event.data;
              document.getElementById("1").appendChild(newChild);
            };

            ws.onopen = (event) => {
              ws.send("socket seems to be open...");
              ws.send("socket says: hello, server!");
            };
          </script>
        </head>

        <body>
          Starting websocket...
          <div id="1">
          </div>
        </body>
      </html>
    """.stripMargin

  def webSocketFlow: Flow[Message, Message, Any] =
    Flow[Message].map {
      case tm: TextMessage =>
        TextMessage(Source.single("Server says back: '") ++ tm.textStream ++ Source.single("' !"))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        TextMessage(Source.single("Server received a binary message..."))
    }

  val webSocketRoute =
    (pathEndOrSingleSlash & get) {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          html
        )
      )
    } ~
      path("greeter") {
        handleWebSocketMessages(socialFlow)
      }

  final case class SocialPost(owner: String, content: String)

  val socialFeed = Source(
    List(
      SocialPost("Martin", "Scala 3 has been announced!"),
      SocialPost("Daniel", "A new RtJMV course is open"),
      SocialPost("Ali", "Hello everyone")
    )
  )

  val socialMessages = socialFeed
    .throttle(1, 2.seconds)
    .map { socialPost =>
      TextMessage(s"${socialPost.owner} said: ${socialPost.content}")
    }

  val socialFlow: Flow[Message, Message, Any] = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),
    socialMessages
  )

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(webSocketRoute)
}
