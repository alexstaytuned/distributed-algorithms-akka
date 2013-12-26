package distributed.links

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.event.LoggingReceive
import distributed.Common._

class StubbornPointToPointLink extends Actor with ActorLogging {

  val fairLossLink = context.actorOf(Props[FairLossPointToPointLink], "FairLossLink")
  val resendAfter = 500.millis
  case class SendWithSender(sendMessage: Send, originalSender: ActorRef)
  var messages = List.empty[SendWithSender]

  case object Resend

  def receive = LoggingReceive {
    case send @ Send(to, msg) =>
      messages ::= SendWithSender(send, sender)
      fairLossLink forward send
      context.system.scheduler.scheduleOnce(resendAfter, self, Resend)
    case Resend =>
      messages.foreach { case SendWithSender(sendMessage, oSender) =>
        fairLossLink.tell(sendMessage, oSender)
      }
      context.system.scheduler.scheduleOnce(resendAfter, self, Resend)
    case deliver @ Deliver(from, msg) if sender == fairLossLink =>
      context.parent ! deliver
    case _ => log.error("Message unknown to StubbornPointToPointLink!")
  }
}
