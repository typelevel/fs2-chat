package fs2chat
package server

import cats.{FlatMap, MonadError}
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, Sync}
import cats.implicits._
import com.comcast.ip4s.Port
import fs2.{Chunk, RaiseThrowable, Stream}
import fs2.concurrent.{Dequeue, Enqueue, Queue}
import fs2.io.tcp.{Socket, SocketGroup}
import io.chrisdavenport.log4cats.Logger
import java.net.InetSocketAddress
import java.util.UUID
import scodec.stream.{StreamDecoder, StreamEncoder}

object Server {
  private case class ConnectedClient[F[_]](
      id: UUID,
      username: Option[Username],
      outgoing: Queue[F, Protocol.ServerCommand],
      incoming: Queue[F, Protocol.ClientCommand])

  private object ConnectedClient {
    def apply[F[_]: Concurrent]: F[ConnectedClient[F]] =
      for {
        id <- Sync[F].delay(UUID.randomUUID)
        outgoing <- Queue.bounded[F, Protocol.ServerCommand](1024)
        incoming <- Queue.bounded[F, Protocol.ClientCommand](32)
      } yield ConnectedClient(id, None, outgoing, incoming)
  }

  private class Clients[F[_]: Sync](
      ref: Ref[F, Map[UUID, ConnectedClient[F]]]) {
    def get(id: UUID): F[Option[ConnectedClient[F]]] = ref.get.map(_.get(id))
    def all: F[List[ConnectedClient[F]]] = ref.get.map(_.values.toList)
    def named: F[List[ConnectedClient[F]]] =
      ref.get.map(_.values.toList.filter(_.username.isDefined))
    def register(state: ConnectedClient[F]): F[Unit] =
      ref.update { oldClients =>
        oldClients + (state.id -> state)
      }
    def unregister(id: UUID): F[Option[ConnectedClient[F]]] =
      ref.modify { old =>
        (old - id, old.get(id))
      }
    def setUsername(clientId: UUID, username: Username): F[Username] =
      ref.modify { clientsById =>
        val usernameToSet =
          determineUniqueUsername(clientsById - clientId, username)
        val updatedClient =
          clientsById.get(clientId).map(_.copy(username = Some(usernameToSet)))
        val updatedClients = updatedClient
          .map(c => clientsById + (clientId -> c))
          .getOrElse(clientsById)
        (updatedClients, usernameToSet)
      }

    def broadcast(cmd: Protocol.ServerCommand): F[Unit] =
      named.flatMap(_.traverse_(_.outgoing.enqueue1(cmd)))
  }

  private object Clients {
    def apply[F[_]: Sync]: F[Clients[F]] =
      Ref[F]
        .of(Map.empty[UUID, ConnectedClient[F]])
        .map(ref => new Clients(ref))
  }

  def start[F[_]: Concurrent: ContextShift: Logger](socketGroup: SocketGroup,
                                                    port: Port) =
    Stream.eval_(Logger[F].info(s"Starting server on port $port")) ++
      Stream
        .eval(Clients[F])
        .flatMap { clients =>
          socketGroup.server[F](new InetSocketAddress(port.value)).map {
            clientSocketResource =>
              val registerClient = ConnectedClient[F].flatTap(clients.register)
              def unregisterClient(state: ConnectedClient[F]) =
                clients.unregister(state.id).flatMap { client =>
                  client
                    .flatMap(_.username)
                    .traverse_(username =>
                      clients.broadcast(Protocol.ServerCommand.Alert(
                        s"$username disconnected.")))
                } *> Logger[F].info(s"Unregistered client ${state.id}")
              Stream
                .bracket(registerClient)(unregisterClient(_))
                .flatMap { client =>
                  Stream.resource(clientSocketResource).flatMap {
                    clientSocket =>
                      handleClient[F](clients, client, clientSocket)
                  }
                }
                .scope
          }
        }
        .parJoinUnbounded

  private def handleClient[F[_]: Concurrent: Logger](
      clients: Clients[F],
      clientState: ConnectedClient[F],
      clientSocket: Socket[F]): Stream[F, Nothing] = {
    logNewClient(clientState, clientSocket) ++
      Stream.eval_(
        clientState.outgoing.enqueue1(
          Protocol.ServerCommand.Alert("Welcome to FS2 Chat!"))) ++
      readClientSocket(clientState.incoming, clientSocket)
        .concurrently(writeClientSocket(clientState.outgoing, clientSocket))
        .concurrently(
          processIncoming(clients,
                          clientState.id,
                          clientState.incoming,
                          clientState.outgoing))
  }.handleErrorWith {
    case _: UserQuit =>
      Stream.eval_(
        Logger[F].info(s"Client quit ${clientState.id}") *> clientSocket.close)
    case err =>
      Stream.eval_(Logger[F].error(
        s"Fatal error for client ${clientState.id} - $err") *> clientSocket.close)
  }

  private def logNewClient[F[_]: FlatMap: Logger](
      clientState: ConnectedClient[F],
      clientSocket: Socket[F]): Stream[F, Nothing] =
    Stream.eval_(clientSocket.remoteAddress.flatMap { clientAddress =>
      Logger[F].info(s"Accepted client ${clientState.id} on $clientAddress")
    })

  private def readClientSocket[F[_]: FlatMap: Logger: RaiseThrowable](
      incoming: Enqueue[F, Protocol.ClientCommand],
      clientSocket: Socket[F]): Stream[F, Nothing] =
    Stream
      .repeatEval(clientSocket.read(1024))
      .unNoneTerminate
      .flatMap(Stream.chunk)
      .through(StreamDecoder.many(Protocol.ClientCommand.codec).toPipeByte[F])
      .through(incoming.enqueue)
      .drain

  private def writeClientSocket[F[_]: FlatMap: Logger: RaiseThrowable](
      outgoing: Dequeue[F, Protocol.ServerCommand],
      clientSocket: Socket[F]): Stream[F, Nothing] =
    outgoing.dequeue
      .through(StreamEncoder.many(Protocol.ServerCommand.codec).toPipe)
      .flatMap(bits => Stream.chunk(Chunk.byteVector(bits.bytes)))
      .through(clientSocket.writes(None))
      .drain

  private def processIncoming[F[_]](
      clients: Clients[F],
      clientId: UUID,
      incoming: Dequeue[F, Protocol.ClientCommand],
      outgoing: Enqueue[F, Protocol.ServerCommand])(
      implicit F: MonadError[F, Throwable]): Stream[F, Nothing] =
    incoming.dequeue.evalMap {
      case Protocol.ClientCommand.RequestUsername(username) =>
        clients.setUsername(clientId, username).flatMap { nameToSet =>
          val alertIfAltered =
            if (username =!= nameToSet)
              outgoing.enqueue1(
                Protocol.ServerCommand.Alert(
                  s"$username already taken, name set to $nameToSet"))
            else F.unit
          alertIfAltered *> outgoing.enqueue1(
            Protocol.ServerCommand.SetUsername(nameToSet)) *>
            clients.broadcast(
              Protocol.ServerCommand.Alert(s"$nameToSet connected."))
        }
      case Protocol.ClientCommand.SendMessage(message) =>
        if (message.startsWith("/")) {
          val cmd = message.tail.toLowerCase
          cmd match {
            case "users" =>
              val usernames = clients.named.map(_.flatMap(_.username).sorted)
              usernames.flatMap(
                users =>
                  outgoing.enqueue1(
                    Protocol.ServerCommand.Alert(users.mkString(", "))))
            case "quit" =>
              outgoing.enqueue1(Protocol.ServerCommand.Disconnect) *>
                F.raiseError(new UserQuit): F[Unit]
            case _ =>
              outgoing.enqueue1(Protocol.ServerCommand.Alert("Unknown command"))
          }
        } else {
          clients.get(clientId).flatMap {
            case Some(client) =>
              client.username match {
                case None =>
                  F.unit // Ignore messages sent before username assignment
                case Some(username) =>
                  val cmd = Protocol.ServerCommand.Message(username, message)
                  clients.broadcast(cmd)
              }
            case None => F.unit
          }
        }
    }.drain

  private def determineUniqueUsername[F[_]](
      clients: Map[UUID, ConnectedClient[F]],
      desired: Username,
      iteration: Int = 0): Username = {
    val username = Username(
      desired.value + (if (iteration > 0) s"-$iteration" else ""))
    clients.find { case (_, client) => client.username === Some(username) } match {
      case None    => username
      case Some(_) => determineUniqueUsername(clients, desired, iteration + 1)
    }
  }
}
