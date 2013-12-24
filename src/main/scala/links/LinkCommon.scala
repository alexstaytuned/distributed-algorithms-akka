package links

import akka.actor.ActorRef

object LinkCommon {
  case class Message(content: String, ts: Long = System.currentTimeMillis())
  case class Send(destination: ActorRef, v: Message)
  case class Deliver(source: ActorRef, v: Message)
}
