package fs2chat

import scodec.Codec
import scodec.codecs._

/** Defines the messages exchanged between the client and server. */
object Protocol {

  private val username: Codec[Username] =
    utf8_32.as[Username]

  /** Base trait for messages sent from the client to the server. */
  enum ClientCommand {
    case RequestUsername(name: Username)
    case SendMessage(value: String)
  }
  object ClientCommand {
    val codec: Codec[ClientCommand] = discriminated[ClientCommand]
      .by(uint8)
      .typecase(1, username.as[RequestUsername])
      .typecase(2, utf8_32.as[SendMessage])
  }

  /** Base trait for messages sent from the server to the client. */
  enum ServerCommand {
    case SetUsername(name: Username)
    case Alert(text: String)
    case Message(name: Username, text: String)
    case Disconnect
  }
  object ServerCommand {
    val codec: Codec[ServerCommand] = discriminated[ServerCommand]
      .by(uint8)
      .typecase(129, username.as[SetUsername])
      .typecase(130, utf8_32.as[Alert])
      .typecase(131, (username :: utf8_32).as[Message])
      .typecase(132, provide(Disconnect))
  }
}
