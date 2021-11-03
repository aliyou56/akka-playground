package streams.graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, GraphDSL, RunnableGraph, Sink, Source }
import akka.stream.{ BidiShape, ClosedShape, Materializer }

/** Used for:
  *    - encrypting/decrypting
  *    - encoding/decoding
  *    -serializing/deserializing
  */
object BidirectionalFlows extends App {

  implicit val system       = ActorSystem("BidirectionalFlows")
  implicit val materializer = Materializer(system)

  /*
    Example: cryptography
   */
  def encrypt(n: Int)(str: String) = str.map(c => (c + n).toChar)
  def decrypt(n: Int)(str: String) = str.map(c => (c - n).toChar)

  // bidiFlow
  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>
    val encryptionFlowShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3)))

    // BidiShape(encryptionFlowShape.in, encryptionFlowShape.out, decryptionFlowShape.in, decryptionFlowShape.out)
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unEncryptedStrings = List("akka", "is", "awesome", "testing", "bidirectional", "flows")
  val unEncryptedSource  = Source(unEncryptedStrings)
  val encryptedSource    = Source(unEncryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val unEncryptedSourceShape = builder.add(unEncryptedSource)
      val encryptedSourceShape   = builder.add(encryptedSource)
      val bidi                   = builder.add(bidiCryptoStaticGraph)
      val encryptedSinkShape     = builder.add(Sink.foreach[String](str => println(s"Encrypted: $str")))
      val decryptedSinkShape     = builder.add(Sink.foreach[String](str => println(s"Decrypted: $str")))

      // format: off
      unEncryptedSourceShape ~> bidi.in1 ; bidi.out1 ~> encryptedSinkShape
      decryptedSinkShape     <~ bidi.out2; bidi.in2 <~ encryptedSourceShape
      // format: on

      ClosedShape
    }
  )

  cryptoBidiGraph.run()

}
