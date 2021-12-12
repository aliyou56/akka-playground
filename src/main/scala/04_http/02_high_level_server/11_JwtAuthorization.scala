package http.high_level_server

import java.util.concurrent.TimeUnit

import scala.util.{ Failure, Success }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import pdi.jwt.{ JwtAlgorithm, JwtClaim, JwtSprayJson }
import spray.json._

/**
 * Authenticate to the server (username + pass, OAuth,...)
 * server sends back a token which can be used for secure endpoints
 * Result: authorization
 *  - not authentication: token is received after authenticating
 *
 * JWT Structure: Part1.Part2.Part3 (encoded inBase64)
 *  Part1: header
 * {
 *  "typ": "JWT",
 *  "alg": "HS256"
 * }
 *  Part2: payload (claims)
 * {
 *  "iss": "company.com",
 *  "exp": 1300819380,
 *  "name": "User",
 *  "admin": "true"
 * }
 *  Part3: signature
 *    take encoded header + "." + encoded claims
 *    sign with the algorithm in the header and a secret key
 *    encode base64
 */

object SecurityDomain extends DefaultJsonProtocol {
  final case class LoginRequest(username: String, password: String)

  implicit val loginRequestFormat = jsonFormat2(LoginRequest)
}

object JwtAuthorization extends App with SprayJsonSupport {

  implicit val system       = ActorSystem("JwtAuthorization")
  implicit val materializer = Materializer(system)

  import SecurityDomain._

  val passwordDb = Map(
    "admin"  -> "admin",
    "aliyou" -> "1234"
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "superSecret"

  def checkPassword(username: String, password: String): Boolean =
    passwordDb.contains(username) && passwordDb(username) == password

  def createToken(username: String, expirationPeriodDays: Int): String = {
    val claims = JwtClaim(
      expiration =
        Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationPeriodDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("company.com")
    )

    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT String
  }

  def isTokenExpired(token: String): Boolean =
    JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
      case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
      case Failure(_)      => true
    }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute: Route =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassword(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute: Route =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) =>
          if (isTokenValid(token))
            if (isTokenExpired(token))
              complete(
                HttpResponse(
                  status = StatusCodes.Unauthorized,
                  entity = "Token expired"
                )
              )
            else
              complete("User accessed authorized endpoint")
          else
            complete(
              HttpResponse(
                status = StatusCodes.Unauthorized,
                entity = "Token is invalid or has been tampered with"
              )
            )
        case _ =>
          complete(
            HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = "No token provided!"
            )
          )
      }
    }

  val route: Route = loginRoute ~ authenticatedRoute

  Http()
    .newServerAt("localhost", 8080)
    .bindFlow(route)
}
