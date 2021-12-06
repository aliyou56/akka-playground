package http.high_level_server

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import spray.json._

case class Person(pin: Int, name: String)

trait PersonJsonSupport extends DefaultJsonProtocol {
  implicit def personFormat = jsonFormat2(Person)
}

object HighLevelExercise extends App with PersonJsonSupport {

  implicit val system       = ActorSystem("HighLevelExercise")
  implicit val materializer = Materializer(system)
  import system.dispatcher

  /**
   * Exercise:
   *
   * - GET /api/people -> retrieve ALL the people you have registered
   * - GET /api/people/pin -> retrieve the person with that PIN, return as JSON
   * - GET /api/people?pin=X -> (same)
   * - POST /api/people with a JSON payload denoting a Person, add it to the DB
   */

  var people: List[Person] = List(
    Person(1, "Alice"),
    Person(2, "Bob")
  )

  def toHttpEntity(payload: String) =
    HttpEntity(
      ContentTypes.`application/json`,
      payload
    )

  val serviceRoute: Route =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter(Symbol("pin").as[Int])) { pin =>
          complete(
            people.find(_.pin == pin) match {
              case Some(person) => toHttpEntity(person.toJson.prettyPrint)
              case None         => StatusCodes.NotFound
            }
          )
        } ~
          pathEndOrSingleSlash {
            complete(toHttpEntity(people.toJson.prettyPrint))
          }
      } ~
        (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>
          val personFuture =
            request
              .entity
              .toStrict(2.seconds)
              .map(_.data.utf8String.parseJson.convertTo[Person])

          onComplete(personFuture) {
            case Success(person) =>
              log.info(s"Got person: $person")
              people = people :+ person
              complete(StatusCodes.OK)
            case Failure(ex) =>
              failWith(ex)
          }

        // "side-effect"
        // personFuture.onComplete {
        //   case Success(person) =>
        //     log.info(s"Got person: $person")
        //     people = people :+ person
        //   case Failure(ex) =>
        //     log.warning(s"Something failed with fetching the person from the entity: $ex")
        // }

        // complete(
        //   personFuture
        //     .map(_ => StatusCodes.OK)
        //     .recover { case _ => StatusCodes.InternalServerError }
        // )
        }
    }

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(serviceRoute)
}
