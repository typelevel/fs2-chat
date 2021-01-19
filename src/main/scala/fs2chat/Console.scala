package fs2chat

import cats.effect.Sync
import cats.implicits._
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.utils.{AttributedStringBuilder, AttributedStyle}

trait Console[F[_]] {
  def println(msg: String): F[Unit]
  def info(msg: String): F[Unit]
  def alert(msg: String): F[Unit]
  def errorln(msg: String): F[Unit]
  def readLine(prompt: String): F[Option[String]]
}

object Console {

  def apply[F[_]](implicit F: Console[F]): F.type = F

  def create[F[_]: Sync]: F[Console[F]] =
    Sync[F].delay {
      new Console[F] {
        private[this] val reader =
          LineReaderBuilder.builder().appName("fs2chat").build()
        reader.setOpt(org.jline.reader.LineReader.Option.ERASE_LINE_ON_FINISH)

        def println(msg: String): F[Unit] =
          Sync[F].blocking(reader.printAbove(msg))

        def info(msg: String): F[Unit] =
          println("*** " + msg)

        def alert(msg: String): F[Unit] =
          println(
            new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE))
              .append("📢 " + msg)
              .toAnsi
          )

        def errorln(msg: String): F[Unit] =
          println(
            new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
              .append("❌ " + msg)
              .toAnsi
          )

        def readLine(prompt: String): F[Option[String]] =
          Sync[F]
            .blocking(Some(reader.readLine(prompt)): Option[String])
            .handleErrorWith {
              case _: EndOfFileException     => (None: Option[String]).pure[F]
              case _: UserInterruptException => (None: Option[String]).pure[F]
              case t                         => Sync[F].raiseError(t)
            }
      }
    }
}
