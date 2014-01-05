package distributed.consensus

import akka.actor._
import distributed.Common._
import distributed.broadcast.BestEffortBroadcast
import scala.util.Random

case object Release
case class Output(result: Boolean)
case class BenOrProposal(phase: Int, round: Int, proposal: Option[Boolean])
case object CheckNext
case class BinaryPropose(v: Boolean)
case class BinaryDecide(v: Boolean)


class CommonCoin extends Actor {
  val rnd = new Random(System.currentTimeMillis())
  def receive = {
    case Release => sender ! Output(rnd.nextBoolean())
  }
}

class RandomizedBinaryConsensus (ownerProcess: ActorRef) extends Actor with ActorLogging {
  var allProcs = List.empty[ActorRef]
  var N = allProcs.size; var f = allProcs.size / 2 - 1 // will be redefined when allProcs is init'd
  val beb = context.actorOf(Props.apply(new BestEffortBroadcast(ownerProcess)), "Broadcast")
  var round = 0
  var phase = 0
  var proposal: Option[Boolean] = None
  var decision: Option[Boolean] = None
  var deliveredDecision = false
  var vall = Map.empty[ActorRef, Option[Boolean]]

  val coin = context.actorOf(Props[CommonCoin], "coin")

  def vallWinner(withMoreThan: Int) = vall.groupBy(_._2).mapValues(_.size) find { case(aValue, votes) => votes > withMoreThan}

  def receive = {
    case i @ Initialize(procs) =>
      allProcs = procs
      N = allProcs.size
      f = allProcs.size / 2 - 1
      beb ! i
    case BinaryPropose(v: Boolean) =>
      log.info(s"${ownerProcess.path.name} proposed $v")
      proposal = Some(v)
      round = 1
      phase = 1
      beb ! Broadcast(Message(BenOrProposal(phase, round, proposal)))
    case Deliver(s, Message(BenOrProposal(ph, rnd, prop), _)) if phase == 1 && rnd == round =>
      vall = vall.updated(s, prop)
      self ! CheckNext
    case CheckNext if vall.size > allProcs.size / 2 && phase == 1 && decision.isEmpty =>
      vallWinner(withMoreThan = allProcs.size / 2) match {
        case Some((w @ Some(winnerValue), _)) =>
          proposal = w
          log.info(s"${ownerProcess.path.name} finished phase 1 with ${proposal.get}")
        case _ =>
          proposal = None
          log.info(s"${ownerProcess.path.name} finished phase 1 with no proposal")
      }
      vall = Map.empty
      phase = 2
      beb ! Broadcast(Message(BenOrProposal(phase, round, proposal)))
    case Deliver(s, Message(BenOrProposal(ph, rnd, prop), _)) if phase == 2 && rnd == round =>
      vall = vall.updated(s, prop)
      self ! CheckNext
    case CheckNext if vall.size >= N - f && phase == 2 && decision.isEmpty =>
      phase = 0
      coin ! Release
    case Output(coinOutput) =>
      vallWinner(withMoreThan = f) match {
        case Some((w @ Some(winnerValue), _)) =>
          decision = w
          log.info(s"${ownerProcess.path.name} decided in $round with ${winnerValue}")
          beb ! Broadcast(Message(BinaryDecide(winnerValue)))
        case _ =>
          vall.find { case(_, aValue) => aValue.isDefined } match {
            case Some((_, conformToValue @ Some(aValue))) =>
              proposal = conformToValue
            case _ => proposal = Some(coinOutput)
          }
          vall = Map.empty
          round += 1; phase = 1
          log.info(s"${ownerProcess.path.name} advancing to round $round with ${proposal.get}")
          beb ! Broadcast(Message(BenOrProposal(phase, round, proposal)))
      }
    case Deliver(s, Message(d @ BinaryDecide(newDecision), _)) if !deliveredDecision =>
      log.info(s"${ownerProcess.path.name} decided $newDecision")
      decision = Some(newDecision)
      context.parent ! d
      deliveredDecision = true
  }
}
