package fs2chat
package client

import cats.ApplicativeError
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import com.comcast.ip4s.{IpAddress, SocketAddress}
import fs2.{Chunk, RaiseThrowable, Stream}
import fs2.concurrent.Queue
import fs2.io.tcp.{Socket, SocketGroup}
import io.chrisdavenport.log4cats.Logger
import java.net.ConnectException
import scala.concurrent.duration._
import scodec.stream.{StreamDecoder, StreamEncoder}

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

  private case class State[F[_]](outgoing: Queue[F, Protocol.ClientCommand],
                                 incoming: Queue[F, Protocol.ServerCommand])

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
            Stream.eval(makeState[F]).flatMap { state =>
              Stream.eval_(state.outgoing.enqueue1(
                Protocol.ClientCommand.RequestUsername(desiredUsername))) ++
                Stream(
                  readServerSocket(state, socket),
                  writeServerSocket(state, socket),
                  processIncoming(state.incoming, console),
                  processOutgoing(state.outgoing, console)
                ).parJoinUnbounded
            }
        }

  private def makeState[F[_]: Concurrent] =
    for {
      outgoing <- Queue.bounded[F, Protocol.ClientCommand](32)
      incoming <- Queue.bounded[F, Protocol.ServerCommand](1024)
    } yield State(outgoing, incoming)

  private def readServerSocket[F[_]: RaiseThrowable](
      state: State[F],
      socket: Socket[F]): Stream[F, Nothing] =
    Stream
      .repeatEval(socket.read(1024))
      .unNone
      .flatMap(Stream.chunk)
      .through(StreamDecoder.many(Protocol.ServerCommand.codec).toPipeByte)
      .through(state.incoming.enqueue)
      .drain

  private def writeServerSocket[F[_]: RaiseThrowable](
      state: State[F],
      socket: Socket[F]): Stream[F, Nothing] =
    state.outgoing.dequeue
      .through(StreamEncoder.many(Protocol.ClientCommand.codec).toPipe)
      .flatMap(bits => Stream.chunk(Chunk.byteVector(bits.bytes)))
      .through(socket.writes(None))
      .drain

  private def processIncoming[F[_]](incoming: Queue[F, Protocol.ServerCommand],
                                    console: Console[F])(
      implicit F: ApplicativeError[F, Throwable]): Stream[F, Nothing] =
    incoming.dequeue.evalMap {
      case Protocol.ServerCommand.Alert(txt) =>
        console.alert(txt)
      case Protocol.ServerCommand.Message(username, txt) =>
        console.message(username, txt)
      case Protocol.ServerCommand.SetUsername(username) =>
        console.alert("Assigned username: " + username)
      case Protocol.ServerCommand.Disconnect =>
        F.raiseError[Unit](new UserQuit)
    }.drain

  private def processOutgoing[F[_]: RaiseThrowable](
      outgoing: Queue[F, Protocol.ClientCommand],
      console: Console[F]): Stream[F, Nothing] =
    Stream
      .repeatEval(console.readLine("> "))
      .flatMap {
        case Some(txt) => Stream(txt)
        case None      => Stream.raiseError[F](new UserQuit)
      }
      .map(txt => Protocol.ClientCommand.SendMessage(txt))
      .through(outgoing.enqueue)
      .drain
}
