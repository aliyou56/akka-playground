package streams.advanced

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ Keep, Sink, Source }

/** Flattening streams
  *   - concatenate: waiting for 1 subStream to finish before taking the next one
  *   - merge: elements for all subStreams are taken in no defined order
  */
object SubStreams extends App {

  implicit val system       = ActorSystem("SubStreams")
  implicit val materializer = Materializer(system)

  // 1. grouping a stream by a certain function
  val wordSource = Source(List("Akka", "is", "amazing", "learning", "subStream"))
  val groups =
    wordSource
      .groupBy(30, w => if (w.isEmpty) '\u0000' else w.toLowerCase().charAt(0))

  groups
    .to(Sink.fold(0) { (count, word) =>
      val newCount = count + 1
      println(s"I just received $word, count is $newCount")
      newCount
    })
  // .run()

  // 2. merge subStreams back
  val textSource = Source(
    List(
      "I lova Akka Streams",
      "this is amazing",
      "learning a bunch of things",
    )
  )

  textSource
    .groupBy(2, _.length % 2)
    .map(_.length) // do expensive computation here
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
  // .run()
  // .onComplete {
  //   case Success(value) => println(s"Total char count: $value")
  //   case Failure(ex)    => println(s"Char computation failed: $ex")
  // }

  // 3. splitting a stream into subStreams, when a condition is met
  val text = Source(
    "I lova Akka Streams\n" +
      "this is amazing\n" +
      "learning a bunch of things\n"
  )

  text
    .splitWhen(_ == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
  // .run()
  // .onComplete {
  //   case Success(value) => println(s"Total char count alternative: $value")
  //   case Failure(ex)    => println(s"Char computation failed: $ex")
  // }

  // 4. Flattening
  val simpleSource = Source(1 to 5)

  simpleSource
    .flatMapConcat(x => Source(x to (2 * x)))
    .runWith(Sink.foreach(println))

  simpleSource
    .flatMapMerge(2, x => Source(x to (2 * x)))
    .runWith(Sink.foreach(println))
}
