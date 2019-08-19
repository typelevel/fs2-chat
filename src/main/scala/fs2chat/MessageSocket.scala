package fs2chat

import cats.effect.Concurrent
import cats.implicits._
import fs2.Chunk
import fs2.Stream
import fs2.concurrent.Queue
import fs2.io.tcp.Socket
import scodec.Decoder
import scodec.Encoder
import scodec.stream.StreamDecoder
import scodec.stream.StreamEncoder

/**
  * Socket which reads a stream of messages of type `In` and allows writing
  * messages of type `Out`.
  */
trait MessageSocket[F[_], In, Out] {
  def read: Stream[F, In]
  def write1(out: Out): F[Unit]
}

object MessageSocket {

  def apply[F[_]: Concurrent, In, Out](
      socket: Socket[F],
      inDecoder: Decoder[In],
      outEncoder: Encoder[Out],
      outputBound: Int
  ): F[MessageSocket[F, In, Out]] =
    for {
      outgoing <- Queue.bounded[F, Out](outputBound)
    } yield
      new MessageSocket[F, In, Out] {
        def read: Stream[F, In] = {
          val readSocket = Stream
            .repeatEval(socket.read(1024))
            .unNoneTerminate
            .flatMap(Stream.chunk)
            .through(StreamDecoder.many(inDecoder).toPipeByte[F])

          val writeOutput = outgoing.dequeue
            .through(StreamEncoder.many(outEncoder).toPipe)
            .flatMap(bits => Stream.chunk(Chunk.byteVector(bits.bytes)))
            .through(socket.writes(None))

          readSocket.concurrently(writeOutput)
        }
        def write1(out: Out): F[Unit] = outgoing.enqueue1(out)
      }
}
