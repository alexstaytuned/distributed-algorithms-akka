package distributed.consensus

import akka.actor._
import distributed.links.PerfectPointToPointLink
import distributed.Common._
import distributed.leader.EventualLeaderDetector
import distributed.broadcast.BestEffortBroadcast

class EpochChange(ownerProcess: ActorRef) extends Actor with ActorLogging {
  var allProcs = List.empty[ActorRef]
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
  val beb = context.actorOf(Props.apply(new BestEffortBroadcast(ownerProcess)), "Broadcast")
  val detector = context.actorOf(Props.apply(new EventualLeaderDetector(ownerProcess)), "LeaderDetector")
  var trusted: ActorRef = _
  var lastTs: Long = 0
  var selfRank = -1
  var ts = selfRank

  def receive = {
    case InitializeEpochChange(procs, initialLeader, processRank) =>
      allProcs = procs; trusted = initialLeader; selfRank = processRank; ts = processRank
      beb ! Initialize(procs)
      detector ! Initialize(procs)
    case Trust(process) =>
      trusted = process
      if(process == ownerProcess) {
        ts += allProcs.size
        beb ! Broadcast(Message(NewEpochMessage(ts)))
      } // else -- not the leader, no-op
    case Deliver(leader, Message(NewEpochMessage(newTs), _)) =>
      if(leader == trusted && newTs > lastTs) {
        lastTs = newTs
        context.parent ! StartEpoch(newTs, leader)
      } else {
        link ! Send(ownerProcess, leader, Message(NewEpochNACK))
      }
    case Deliver(subordinate, Message(NewEpochNACK, _)) =>
      if(trusted == ownerProcess) {
        ts += allProcs.size
        beb ! Broadcast(Message(NewEpochMessage(ts)))
      } // else -- not the leader, no-op
  }

  case object NewEpochNACK
}
