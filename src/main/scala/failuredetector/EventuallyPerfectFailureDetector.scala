package failuredetector

import akka.actor._
import links.PerfectPointToPointLink
import scala.concurrent.duration._
import failuredetector.FailureDetectorCommon._
import scala.concurrent.ExecutionContext.Implicits.global
import links.LinkCommon._
import scala.concurrent.Await

object FailureDetectorCommon {
  case class Initialize(allProcs: List[ActorRef])
  case object Gather
  case class Suspect(process: ActorRef)
  case class Restore(process: ActorRef)
  val HeartbeatRequest = "HeartBeatRequest"
  val HeartbeatReply = "HeartBeatReply"
}

class EventuallyPerfectFailureDetector extends Actor with ActorLogging {

  var allProcs: List[ActorRef] = List.empty[ActorRef]
  var fdsToProcs: Map[ActorRef, ActorRef] = Map.empty
  var alive: List[ActorRef] = List.empty[ActorRef]
  var suspected: List[ActorRef] = List.empty[ActorRef]
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
  var delay = 300 millis

  def receive = {
    case Initialize(all) =>
      allProcs = all
      alive = all
      context.system.scheduler.scheduleOnce(delay, self, Gather)
    case Gather =>
      if(alive.toSet.intersect(suspected.toSet).size > 0) {
        delay = delay + 300.millis
      } // else -- keep the delay constant, no errors were found
      allProcs.foreach { proc =>
        if(! alive.contains(proc) && ! suspected.contains(proc)) {
          suspected ::= proc
          context.parent ! Suspect(proc)
        } else if(alive.contains(proc) && suspected.contains(proc)) {
          context.parent ! Restore(proc)
        }
        link ! Send(proc, Message(HeartbeatRequest))
      }
      alive = List.empty[ActorRef]
      context.system.scheduler.scheduleOnce(delay, self, Gather)
    case Deliver(s, Message(HeartbeatReply, _)) =>
      alive ::= s
    case Deliver(s, Message(HeartbeatRequest, _)) =>
      link.tell(Send(s, Message(HeartbeatReply)), context.parent)
  }
}
