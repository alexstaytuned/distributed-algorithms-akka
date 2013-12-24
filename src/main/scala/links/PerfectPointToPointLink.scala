package links

import akka.actor._
import links.LinkCommon._
import scala.collection.mutable

class PerfectPointToPointLink extends Actor with ActorLogging {

  val stubbornLink = context.actorOf(Props[StubbornPointToPointLink], "StubbornLink")
  var delivered = List.empty[Deliver]

  def receive = {
    case send @ Send(to, msg) =>
      stubbornLink forward Send(to, msg)
    case d @ Deliver(from, msg) if sender == stubbornLink =>
      if(! delivered.contains(d)) {
        delivered ::= d
        context.parent ! Deliver(from, msg)
      } // else -- message is a duplicate
    case x => log.error(s"Message unknown to PerfectPointToPointLink: $x from $sender")
  }
}