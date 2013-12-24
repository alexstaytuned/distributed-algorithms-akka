package links

import akka.actor._
import links.LinkCommon._
import scala.util.Random
import akka.event.LoggingReceive

class FairLossPointToPointLink extends Actor with ActorLogging {

  val rnd = new Random(System.currentTimeMillis())
  val dropPercentage = 0

  def receive = LoggingReceive {
    case Send(to, message) =>
      if(rnd.nextInt(100) > dropPercentage) {
        val pathForOther = mergePaths(to.path.toString, self.path.toString)
        context.actorSelection(pathForOther) ! Deliver(sender, message)
      } // else -- drop
    case d @ Deliver(from, message) =>
      context.parent ! d
  }

  def mergePaths(destination: String, deepPath: String): String = {
    val destinationPieces = destination.split("/")
    val deepPathPieces = deepPath.split("/")
    (destinationPieces ++ deepPathPieces.drop(destinationPieces.size)).mkString("/")
  }
}
