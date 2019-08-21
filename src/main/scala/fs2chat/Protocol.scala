package fs2chat

import scodec.Codec
import scodec.codecs._

/** Defines the messages exchanged between the client and server. */
object Protocol {

  private val username: Codec[Username] =
    utf8_32.as[Username]

  /** Base trait for messages sent from the client to the server. */
  sealed trait ClientCommand
  object ClientCommand {
    case class RequestUsername(name: Username) extends ClientCommand
    case class SendMessage(value: String) extends ClientCommand

    val codec: Codec[ClientCommand] = discriminated[ClientCommand]
      .by(uint8)
      .typecase(1, username.as[RequestUsername])
      .typecase(2, utf8_32.as[SendMessage])
  }

  /** Base trait for messages sent from the server to the client. */
  sealed trait ServerCommand
  object ServerCommand {
    case class SetUsername(name: Username) extends ServerCommand
    case class Alert(text: String) extends ServerCommand
    case class Message(name: Username, text: String) extends ServerCommand
    case object Disconnect extends ServerCommand

    val codec: Codec[ServerCommand] = discriminated[ServerCommand]
      .by(uint8)
      .typecase(129, username.as[SetUsername])
      .typecase(130, utf8_32.as[Alert])
      .typecase(131, (username :: utf8_32).as[Message])
      .typecase(132, provide(Disconnect))
  }
}
