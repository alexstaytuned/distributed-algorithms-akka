package distributed.links

import akka.actor._
import scala.util.Random
import akka.event.LoggingReceive
import distributed.Common._

class FairLossPointToPointLink extends Actor with ActorLogging {

  val rnd = new Random(System.currentTimeMillis())
  val dropPercentage = 0

  def receive = LoggingReceive {
    case Send(to, message) =>
      if(rnd.nextInt(100) > dropPercentage) {
        /**
         * This is where the boundary of actor relationships is crossed. All previous messages
         * from the sender up to this point were just forwarded to child actors.
         *
         * Now, we have to find the fairLossLink at the destination and Deliver the message there.
         * The way that fairLossLink is found is jenky - can be improved
         */
        val pathForOther = mergePaths(to.path.toString, self.path.toString)
        context.actorSelection(pathForOther) ! Deliver(sender, message)
      } // else -- drop it, simulating a real loss link
    case d @ Deliver(from, message) =>
      context.parent ! d
  }

  def mergePaths(destination: String, deepPath: String): String = {
    val destinationPieces = destination.split("/")
    val deepPathPieces = deepPath.split("/")
    (destinationPieces ++ deepPathPieces.drop(destinationPieces.size)).mkString("/")
  }
}
