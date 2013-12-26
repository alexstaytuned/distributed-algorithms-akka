package distributed

import akka.actor.ActorRef

object Common {
  case class Initialize(allProcs: List[ActorRef])
  case class Message(content: String, ts: Long = System.currentTimeMillis())

  // links
  case class Send(source: ActorRef, destination: ActorRef, v: Message)
  case class Deliver(source: ActorRef, v: Message)

  // leader
  case class Trust(leader: ActorRef)

  // failure detector
  case object Gather
  case class Suspect(process: ActorRef)
  case class Restore(process: ActorRef)
  val HeartbeatRequest = "HeartBeatRequest"
  val HeartbeatReply = "HeartBeatReply"

  // consensus common
  case class StartEpoch(ts: Long, leader: ActorRef)
  case class InitializeEpochChange(allProcs: List[ActorRef], lastTs: Long, leader: ActorRef, selfRank: Int)

  // broadcast common
  case class Broadcast(m: Message)
}