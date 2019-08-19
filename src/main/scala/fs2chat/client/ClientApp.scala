package fs2chat
package client

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import com.comcast.ip4s._
import com.monovore.decline._
import fs2.io.tcp.SocketGroup
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

object ClientApp extends IOApp {
  private val argsParser: Command[(Username, SocketAddress[IpAddress])] =
    Command("fs2chat-client", "FS2 Chat Client") {
      (
        Opts.option[String]("username", "Desired username").map(Username.apply),
        Opts
          .option[String]("address", "Address of chat server")
          .withDefault("127.0.0.1")
          .mapValidated(p => IpAddress(p).toValidNel("Invalid IP address")),
        Opts
          .option[Int]("port", "Port of chat server")
          .withDefault(5555)
          .mapValidated(p => Port(p).toValidNel("Invalid port number"))
      ).mapN {
        case (desiredUsername, ip, port) =>
          desiredUsername -> SocketAddress(ip, port)
      }
    }

  def run(args: List[String]): IO[ExitCode] = {
    argsParser.parse(args) match {
      case Left(help) => IO(System.err.println(help)).as(ExitCode.Error)
      case Right((desiredUsername, address)) =>
        Blocker[IO]
          .use { blocker =>
            Console[IO](blocker).flatMap { console =>
              SocketGroup[IO](blocker).use { socketGroup =>
                Slf4jLogger.create[IO].flatMap { implicit logger =>
                  Client
                    .start[IO](console, socketGroup, address, desiredUsername)
                    .compile
                    .drain
                }
              }
            }
          }
          .as(ExitCode.Success)
    }
  }
}
