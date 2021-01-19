package fs2chat
package server

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.comcast.ip4s._
import com.monovore.decline._

object ServerApp extends IOApp {
  private val argsParser: Command[Port] =
    Command("fs2chat-server", "FS2 Chat Server") {
      Opts
        .option[Int]("port", "Port to bind for connection requests")
        .withDefault(5555)
        .mapValidated(p => Port(p).toValidNel("Invalid port number"))
    }

  def run(args: List[String]): IO[ExitCode] =
    argsParser.parse(args) match {
      case Left(help) => IO(System.err.println(help)).as(ExitCode.Error)
      case Right(port) =>
        Console
          .create[IO]
          .flatMap(implicit console => Server.start[IO](port).compile.drain)
          .as(ExitCode.Success)
    }
}
