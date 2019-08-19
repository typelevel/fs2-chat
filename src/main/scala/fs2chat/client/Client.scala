package fs2chat
package client

import cats.ApplicativeError
import cats.effect.{Concurrent, ContextShift, Timer}
import com.comcast.ip4s.{IpAddress, SocketAddress}
import fs2.{RaiseThrowable, Stream}
import fs2.io.tcp.SocketGroup
import io.chrisdavenport.log4cats.Logger
import java.net.ConnectException
import scala.concurrent.duration._

object Client {
  def start[F[_]: Concurrent: ContextShift: Timer: Logger](
      console: Console[F],
      socketGroup: SocketGroup,
      address: SocketAddress[IpAddress],
      desiredUsername: Username): Stream[F, Unit] =
    connect(console, socketGroup, address, desiredUsername).handleErrorWith {
      case _: ConnectException =>
        val retryDelay = 5.seconds
        Stream.eval_(
          console.errorln(s"Failed to connect. Retrying in $retryDelay.")) ++
          start(console, socketGroup, address, desiredUsername)
            .delayBy(retryDelay)
      case _: UserQuit => Stream.empty
    }

  private def connect[F[_]: Concurrent: ContextShift: Logger](
      console: Console[F],
      socketGroup: SocketGroup,
      address: SocketAddress[IpAddress],
      desiredUsername: Username) =
    Stream.eval_(console.info(s"Connecting to server $address")) ++
      Stream
        .resource(socketGroup.client[F](address.toInetSocketAddress))
        .flatMap { socket =>
          Stream.eval_(console.info("🎉 Connected! 🎊")) ++
            Stream
              .eval(
                MessageSocket(socket,
                              Protocol.ServerCommand.codec,
                              Protocol.ClientCommand.codec,
                              128))
              .flatMap { messageSocket =>
                Stream.eval_(messageSocket.write1(
                  Protocol.ClientCommand.RequestUsername(desiredUsername))) ++
                  processIncoming(messageSocket, console).concurrently(
                    processOutgoing(messageSocket, console))
              }
        }

  private def processIncoming[F[_]](
      messageSocket: MessageSocket[F,
                                   Protocol.ServerCommand,
                                   Protocol.ClientCommand],
      console: Console[F])(
      implicit F: ApplicativeError[F, Throwable]): Stream[F, Unit] =
    messageSocket.read.evalMap {
      case Protocol.ServerCommand.Alert(txt) =>
        console.alert(txt)
      case Protocol.ServerCommand.Message(username, txt) =>
        console.println(s"$username> $txt")
      case Protocol.ServerCommand.SetUsername(username) =>
        console.alert("Assigned username: " + username)
      case Protocol.ServerCommand.Disconnect =>
        F.raiseError[Unit](new UserQuit)
    }

  private def processOutgoing[F[_]: RaiseThrowable](
      messageSocket: MessageSocket[F,
                                   Protocol.ServerCommand,
                                   Protocol.ClientCommand],
      console: Console[F]): Stream[F, Unit] =
    Stream
      .repeatEval(console.readLine("> "))
      .flatMap {
        case Some(txt) => Stream(txt)
        case None      => Stream.raiseError[F](new UserQuit)
      }
      .map(txt => Protocol.ClientCommand.SendMessage(txt))
      .evalMap(messageSocket.write1)
}
