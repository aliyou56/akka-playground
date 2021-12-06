package http.high_level_server

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer

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

object MarshallingJson extends App {

  implicit val system       = ActorSystem("MarshallingJson")
  implicit val materializer = Materializer(system)

  import GameAreaMap._

  val gameMap = system.actorOf(Props[GameAreaMap](), "GameAreaMap")
  val players = List(
    Player("martin", "warrior", 70),
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

  val serviceRoute: Route =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          reject
        } ~
          (path(Segment) | parameter(Symbol("nickname"))) { nickname =>
            reject
          } ~
          pathEndOrSingleSlash {
            reject
          }
      } ~
        post {
          reject
        } ~ delete {
          reject
        }
    }
}
