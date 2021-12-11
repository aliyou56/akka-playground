package http.high_level_server

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ MethodRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

final case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat = jsonFormat3(Book)
}

// format: off
class RouteDSLSpec extends AnyWordSpec
  with Matchers
  with ScalatestRouteTest
  with BookJsonProtocol {
// format: on

  import RouteDSLSpec._

  "A digital library backend" should {
    "return all the books in the library" in {
      // send HTTP request though the endpoint to be tested and inspect the response
      Get("/api/book") ~> libraryRoute ~> check {
        // assertions
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books
      }
    }

    "return a book by hitting the query parameter endpoint" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(Book(2, "JRR Token", "The Lord of the Rings"))
      }
    }

    "return a book by calling the endpoint with id in the path" in {
      Get("/api/book/2") ~> libraryRoute ~> check {
        response.status shouldBe StatusCodes.OK

        val strictEntityFuture = response.entity.toStrict(1.second)
        val strictEntity       = Await.result(strictEntityFuture, 1.second)

        strictEntity.contentType shouldBe ContentTypes.`application/json`
        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(2, "JRR Token", "The Lord of the Rings"))
      }
    }

    "insert a book into the 'database'" in {
      val book = Book(5, "Steven Pressfield", "The war of Art")
      Post("/api/book", book) ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        assert(books.contains(book))
        books should contain(book)
      }
    }

    "not accept other method than POST and GET" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        rejections should not be empty
        rejections.should(not).be(empty)

        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length shouldBe 2
      }
    }

    "return all the books of a a given author" in {
      Get("/api/book/author/GRR%20Martin") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books.filter(_.author == "GRR Martin")
      }
    }
  }
}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport {

  // code under test
  var books = List(
    Book(1, "Harper Lee", "To kill a Mockingbird"),
    Book(2, "JRR Token", "The Lord of the Rings"),
    Book(3, "GRR Martin", "A song of Ice and Fire"),
    Book(4, "Tony Robbins", "Awaken the Giant Within")
  )

  /**
   * GET /api/book -> returns all the books in the library
   * GET /api/book/X -> returns a single book with id X
   * GET /api/book?id=X - same
   * GET /api/book - adds a new book to the library
   * GET /api/book/author/X -> returns all the books from the author X
   */

  val libraryRoute: Route =
    pathPrefix("api" / "book") {
      (path("author" / Segment) & get) { author =>
        complete(books.filter(_.author == author))
      } ~
        get {
          (path(IntNumber) | parameter(Symbol("id").as[Int])) { id =>
            complete(books.find(_.id == id))
          } ~
            pathEndOrSingleSlash {
              complete(books)
            }
        } ~
        post {
          entity(as[Book]) { book =>
            books = books :+ book
            complete(StatusCodes.OK)
          }
        }
    }
}
