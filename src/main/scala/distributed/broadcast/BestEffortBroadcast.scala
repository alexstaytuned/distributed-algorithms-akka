package distributed.broadcast

import akka.actor._
import distributed.Common._
import distributed.links.PerfectPointToPointLink

class BestEffortBroadcast(ownerProcess: ActorRef) extends Actor with ActorLogging {
  var allProcs = List.empty[ActorRef]
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")

  def receive = {
    case Initialize(procs) =>
      allProcs = procs
    case Broadcast(m) =>
      allProcs.foreach { proc =>
        link ! Send(ownerProcess, proc, m)
      }
    case d @ Deliver(source, m) =>
      context.parent ! d
  }
}
