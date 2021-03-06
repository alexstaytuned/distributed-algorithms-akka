package distributed.links

import akka.actor._
import distributed.Common._

class PerfectPointToPointLink extends Actor with ActorLogging {

  val stubbornLink = context.actorOf(Props[StubbornPointToPointLink], "StubbornLink")
  var delivered = List.empty[Deliver]

  def receive = {
    case send @ Send(from, to, msg) =>
      stubbornLink ! send
    case d @ Deliver(from, msg) if sender == stubbornLink =>
      if(! delivered.contains(d)) {
        delivered ::= d
        context.parent ! Deliver(from, msg)
      } // else -- message is a duplicate
    case x => log.error(s"Message unknown to PerfectPointToPointLink: $x from $sender")
  }
}