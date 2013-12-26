package distributed.consensus

import akka.actor._
import distributed.links.PerfectPointToPointLink
import distributed.Common._
import distributed.leader.EventualLeaderDetector

class EpochChange extends Actor with ActorLogging {
  var allProcs = List.empty[ActorRef]
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
  val beb = context.actorOf(Props[PerfectPointToPointLink], "Broadcast")
  val detector = context.actorOf(Props[EventualLeaderDetector], "LeaderDetector")
  var trusted: ActorRef = _
  var lastTs: Long = 0
  var selfRank = -1

  def receive = {
    case InitializeEpochChange(procs, lTs, l, sRank) =>
      allProcs = procs
      lastTs = lTs
      trusted = l
      selfRank = sRank
      trusted

  }
}
