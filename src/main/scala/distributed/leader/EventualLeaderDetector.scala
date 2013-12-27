package distributed.leader

import akka.actor._
import akka.event.LoggingReceive
import distributed.failuredetector.EventuallyPerfectFailureDetector
import scala.concurrent.ExecutionContext.Implicits.global
import distributed.Common._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import distributed.Common.Initialize

class EventualLeaderDetector(ownerProcess: ActorRef) extends Actor with ActorLogging {

  case object CheckLeadership

  var allProcs = List.empty[ActorRef]
  val detector = context.actorOf(Props.apply(new EventuallyPerfectFailureDetector(ownerProcess)), "FailureDetector")
  val suspected = ArrayBuffer.empty[ActorRef]

  var leader: Option[ActorRef] = None
  val checkFrequency = 100.millis
  context.system.scheduler.schedule(checkFrequency, checkFrequency, self, CheckLeadership)

  def receive = LoggingReceive {
    case Initialize(nodes) =>
      detector ! Initialize(nodes)
      allProcs = nodes
    case Suspect(node) =>
      suspected += node
    case Restore(node) =>
      suspected -= node
    case CheckLeadership =>
      maxRank(allProcs.toSet.diff(suspected.toSet).toSeq) map { newLeader =>
        leader match {
          case None =>
            trust(newLeader)
          case Some(l) if l != newLeader =>
            trust(newLeader)
          case _ => // same leader
        }
      }
  }

  def maxRank(procs: Seq[ActorRef]): Option[ActorRef] = {
    procs.size match {
      case 0 => None
      case _ =>
        val (max, _) = procs.zip(procs.map(_.hashCode())).maxBy { case (proc, hash) => hash }
        Some(max)
    }

  }

  def trust(newLeader: ActorRef) = {
    leader = Some(newLeader)
    context.parent ! Trust(newLeader)
  }
}
