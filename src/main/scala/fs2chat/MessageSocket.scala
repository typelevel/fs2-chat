package fs2chat

import cats.effect.Concurrent
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import fs2.io.net.Socket
import fs2.interop.scodec.{StreamDecoder, StreamEncoder}
import scodec.{Decoder, Encoder}

/** Socket which reads a stream of messages of type `In` and allows writing messages of type `Out`.
  */
trait MessageSocket[F[_], In, Out]:
  def read: Stream[F, In]
  def write1(out: Out): F[Unit]

object MessageSocket:

  def apply[F[_]: Concurrent, In, Out](
      socket: Socket[F],
      inDecoder: Decoder[In],
      outEncoder: Encoder[Out],
      outputBound: Int
  ): F[MessageSocket[F, In, Out]] =
    for outgoing <- Queue.bounded[F, Out](outputBound)
    yield new MessageSocket[F, In, Out] {
      def read: Stream[F, In] =
        val readSocket = socket.reads
          .through(StreamDecoder.many(inDecoder).toPipeByte[F])

        val writeOutput = Stream
          .fromQueueUnterminated(outgoing)
          .through(StreamEncoder.many(outEncoder).toPipeByte)
          .through(socket.writes)

        readSocket.concurrently(writeOutput)
      def write1(out: Out): F[Unit] = outgoing.offer(out)
    }
