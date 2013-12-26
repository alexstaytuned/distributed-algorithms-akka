package distributed.failuredetector

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import distributed.Initialize
import distributed.links.PerfectPointToPointLink
import distributed.links.LinkCommon._
import distributed.failuredetector.FailureDetectorCommon._

object FailureDetectorCommon {
  case object Gather
  case class Suspect(process: ActorRef)
  case class Restore(process: ActorRef)
  val HeartbeatRequest = "HeartBeatRequest"
  val HeartbeatReply = "HeartBeatReply"
}

class EventuallyPerfectFailureDetector extends Actor with ActorLogging {

  var allProcs: List[ActorRef] = List.empty[ActorRef]

  // use Paths because the responses may come from children of allProcs members
  // this means we need to normalize those to allProcs paths
  var alivePaths: List[ActorPath] = List.empty
  var suspectedPaths: List[ActorPath] = List.empty
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
  val delayIncrement = 300.millis
  var delay = delayIncrement

  def receive = {
    case Initialize(all) =>
      allProcs = all
      alivePaths = all.map(_.path)
      context.system.scheduler.scheduleOnce(delay, self, Gather)
    case Gather =>
      if(alivePaths.toSet.intersect(suspectedPaths.toSet).size > 0) {
        delay = delay + delayIncrement
      } // else -- keep the delay constant, no errors were found
      allProcs.foreach { proc =>
        val procPath = proc.path
        if(! alivePaths.contains(procPath) && ! suspectedPaths.contains(procPath)) {
          suspectedPaths ::= procPath
          context.parent ! Suspect(proc)
        } else if(alivePaths.contains(procPath) && suspectedPaths.contains(procPath)) {
          context.parent ! Restore(proc)
        }
        link ! Send(proc, Message(HeartbeatRequest))
      }
      alivePaths = List.empty
      context.system.scheduler.scheduleOnce(delay, self, Gather)
    case Deliver(s, Message(HeartbeatReply, _)) =>
      alivePaths ::= normalizedPath(s)
    case Deliver(s, Message(HeartbeatRequest, _)) =>
      link ! Send(s, Message(HeartbeatReply))
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
