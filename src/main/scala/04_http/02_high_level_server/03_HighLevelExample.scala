package http.high_level_server

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import spray.json._

import http.utils.Domain._

object HighLevelExample extends App with GuitarStoreJsonProtocol {
  implicit val system       = ActorSystem("HighLevelExample")
  implicit val materializer = Materializer(system)
  import system.dispatcher

  /**
   * GET /api/guitar fetches ALL the guitars in the store
   * GET /api/guitar?id=x fetches the guitar with id x
   * GET api/guitar/x fetches guitar with id x
   * GET /api/guitar/inventory?inStock=true
   */

  import http.utils.Domain.GuitarDB._

  /** Setup */
  val guitarDB = system.actorOf(Props[GuitarDB](), "HighLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1r"),
  )

  guitarList.foreach(guitar => guitarDB ! CreateGuitar(guitar))

  implicit val timeout = Timeout(2.seconds)

  val guitarServerRoute =
    path("api" / "guitar") {
      parameter(Symbol("id").as[Int]) { guitarId =>
        get {
          val guitarFuture: Future[Option[Guitar]] =
            (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]

          val entityFuture = guitarFuture.map { guitarOpt =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOpt.toJson.prettyPrint,
            )
          }

          complete(entityFuture)
        }
      } ~
        get {
          val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]

          val entityFuture = guitarsFuture.map { guitars =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint,
            )
          }

          complete(entityFuture)
        }
    } ~
      path("api" / "guitar" / IntNumber) { guitarId =>
        get {
          val guitarFuture: Future[Option[Guitar]] =
            (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]

          val entityFuture = guitarFuture.map { guitarOpt =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOpt.toJson.prettyPrint,
            )
          }

          complete(entityFuture)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
          parameter(Symbol("inStock").as[Boolean]) { inStock =>
            val guitarsFuture: Future[List[Guitar]] =
              (guitarDB ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]

            val entityFuture = guitarsFuture.map { guitars =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint,
              )
            }

            complete(entityFuture)
          }
        }
      }

  def toHttpEntity(payload: String) =
    HttpEntity(
      ContentTypes.`application/json`,
      payload,
    )

  val simplifiedGuitarsServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter(Symbol("inStock").as[Boolean]) { inStock =>
          complete(
            (guitarDB ? FindGuitarsInStock(inStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
        (path(IntNumber) | parameter(Symbol("id").as[Int])) { guitarId =>
          complete(
            (guitarDB ? FindGuitar(guitarId))
              .mapTo[Option[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        } ~
        pathEndOrSingleSlash {
          complete(
            (guitarDB ? FindAllGuitars)
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
    }

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(simplifiedGuitarsServerRoute)
}
