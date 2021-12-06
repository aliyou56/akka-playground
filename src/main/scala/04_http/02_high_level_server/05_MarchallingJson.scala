package http.high_level_server

import scala.concurrent.duration._

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.pattern.ask
import akka.util.Timeout
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes

final case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayerByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}

final class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._

  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all players")
      sender() ! players.values.toList

    case GetPlayer(nickname) =>
      log.info(s"Getting player with nickname: $nickname")
      sender() ! players.get(nickname)

    case GetPlayerByClass(characterClass) =>
      log.info(s"Getting players with the character class: $characterClass")
      sender() ! players.values.filter(_.characterClass == characterClass)

    case AddPlayer(player) =>
      log.info(s"Trying to add player: $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess

    case RemovePlayer(player) =>
      log.info(s"Trying to remove player: $player")
      players = players - player.nickname
      sender() ! OperationSuccess
  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

// format: off
object MarshallingJson extends App 
  with PlayerJsonProtocol 
  with SprayJsonSupport {
// format: on

  implicit val system       = ActorSystem("MarshallingJson")
  implicit val materializer = Materializer(system)
  import system.dispatcher

  import GameAreaMap._

  val gameMap = system.actorOf(Props[GameAreaMap](), "GameAreaMap")
  val players = List(
    Player("martin", "Warrior", 70),
    Player("roland007", "Elf", 67),
    Player("daniel_rock03", "wizard", 30)
  )

  players.foreach(player => gameMap ! AddPlayer(player))

  /**
   * - GET /api/player -> returns all the the players in the map, as JSON
   * - GET /api/player/nickname -> returns the player with the given nickname (as JSON)
   * - GET /api/player?nickname=X -> does the same
   * - GET /api/player/class/(characterClass) -> returns all the players with the given character class
   * - POST /api/player with JSON payload, adds the player to the map
   * - DELETE /api/player with JSON payload, removes the player from the map
   */

  implicit val timeout = Timeout(2.seconds)

  val serviceRoute: Route =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          complete(
            (gameMap ? GetPlayerByClass(characterClass)).mapTo[List[Player]]
          )
        } ~
          (path(Segment) | parameter(Symbol("nickname"))) { nickname =>
            val playerOptFuture = (gameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
            complete(playerOptFuture)
          } ~
          pathEndOrSingleSlash {
            complete((gameMap ? GetAllPlayers).mapTo[List[Player]])
          }
      } ~
        post {
          entity(as[Player]) { player =>
            complete(
              (gameMap ? AddPlayer(player)).map(_ => StatusCodes.OK)
            )
          }
        } ~ (delete & pathEndOrSingleSlash) {
          entity(as[Player]) { player =>
            complete(
              (gameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK)
            )
          }
        }
    }

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(serviceRoute)
}
