package distributed.links

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.event.LoggingReceive
import distributed.Common._

class StubbornPointToPointLink extends Actor with ActorLogging {

  val fairLossLink = context.actorOf(Props[FairLossPointToPointLink], "FairLossLink")
  val resendAfter = 500.millis
  var messages = List.empty[Send]

  case object Resend

  def receive = LoggingReceive {
    case send @ Send(from, to, msg) =>
      messages ::= send
      fairLossLink ! send
//      context.system.scheduler.scheduleOnce(resendAfter, self, Resend)
    case Resend =>
      messages.foreach { msg =>
        fairLossLink ! msg
      }
      context.system.scheduler.scheduleOnce(resendAfter, self, Resend)
    case deliver @ Deliver(from, msg) if sender == fairLossLink =>
      context.parent ! deliver
    case _ => log.error("Message unknown to StubbornPointToPointLink!")
  }
}
