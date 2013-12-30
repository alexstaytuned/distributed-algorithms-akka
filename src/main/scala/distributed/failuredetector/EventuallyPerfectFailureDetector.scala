package distributed.failuredetector

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import distributed.links.PerfectPointToPointLink
import distributed.Common._

class EventuallyPerfectFailureDetector(ownerProcess: ActorRef) extends Actor with ActorLogging {

  var allProcs: List[ActorRef] = List.empty[ActorRef]

  // use Paths because the responses may come from children of allProcs members
  // this means we need to normalize those to allProcs paths
  var alive: List[ActorRef] = List.empty
  var suspected: List[ActorRef] = List.empty
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
  val delayIncrement = 300.millis
  var delay = delayIncrement

  def receive = {
    case Initialize(all) =>
      allProcs = all
      alive = all
      context.system.scheduler.scheduleOnce(delay, self, Gather)
    case Gather =>
      if(alive.toSet.intersect(suspected.toSet).size > 0) {
        delay = delay + delayIncrement
      } // else -- keep the delay constant, no errors were found
      allProcs.foreach { proc =>
        if(! alive.contains(proc) && ! suspected.contains(proc)) {
          suspected ::= proc
          context.parent ! Suspect(proc)
        } else if(alive.contains(proc) && suspected.contains(proc)) {
          context.parent ! Restore(proc)
        }
        link ! Send(ownerProcess, proc, Message(HeartbeatRequest))
      }
      alive = List.empty
      context.system.scheduler.scheduleOnce(delay, self, Gather)
    case Deliver(s, Message(HeartbeatReply, _)) =>
      alive ::= s
    case Deliver(s, Message(HeartbeatRequest, _)) =>
      link ! Send(ownerProcess, s, Message(HeartbeatReply))
  }

  def normalizedPath(a: ActorRef) = {
    val initialPathSize = allProcs.head.path.elements.size
    def shorten(path: ActorPath): ActorPath = {
      if(path.elements.size > initialPathSize) shorten(path.parent)
      else path
    }
    shorten(a.path)
  }
}
