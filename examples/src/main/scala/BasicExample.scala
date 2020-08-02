
import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import cats.data._
import cats.effect.concurrent._
import fs2.io.tcp._
import fs2.concurrent.Queue
import java.net.InetSocketAddress
import fs2._
import scala.concurrent.duration._
import _root_.io.chrisdavenport.keypool.KeyPoolBuilder
import java.net.InetAddress
import _root_.io.chrisdavenport.keypool.Reusable.DontReuse
import _root_.io.chrisdavenport.rediculous._

object BasicExample extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val r = for {
      blocker <- Blocker[IO]
      sg <- SocketGroup[IO](blocker)
      connection <- RedisConnection.queued[IO](sg, new InetSocketAddress("localhost", 6379), maxQueued = 20000, workers = 1)
    } yield connection

    r.use {client =>
        // val r = (
        //   RedisCommands.ping[IO],
        //   RedisCommands.get[IO]("foo"),
        //   RedisCommands.set[IO]("foo", "value"),
        //   RedisCommands.get[IO]("foo")
        // ).parTupled

      val r2 = List.fill(1000)(RedisCommands.ping[IO]).parSequence

      val now = IO(java.time.Instant.now)
      (
        now,
        Stream(()).covary[IO].repeat.map(_ => Stream.evalSeq(r2.run(client))).parJoin(10).take(1000000).compile.drain,
        now
      ).mapN{
        case (before, _, after) => (after.toEpochMilli() - before.toEpochMilli()).millis
      }.flatMap{ duration => 
        IO(println(s"Operation took ${duration}"))
      }
    } >>
      IO.pure(ExitCode.Success)
    
  }

}