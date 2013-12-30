package distributed

import akka.actor.ActorRef

object Common {
  case class Initialize(allProcs: List[ActorRef])
  case class Message(content: Any, ts: Long = System.currentTimeMillis())

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
  case class InitializeEpochChange(allProcs: List[ActorRef], leader: ActorRef, selfRank: Int)
  case class InitializeEpochConsensus(allProcs: List[ActorRef], leader: ActorRef, ts: Long, state: EpochState)
  case class NewEpochMessage(ts: Long)
  case class Propose(value: Int)
  case class EpochState(ts: Long, value: Option[Int])
  case class Decide(v: Int)
  case object Abort
  case class Aborted(state: EpochState)

  // broadcast common
  case class Broadcast(m: Message)
  val EpochConsensusRead = "Read"

}