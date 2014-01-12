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

sealed trait Phase
case object Phase0 extends Phase
case object Phase1 extends Phase
case object Phase2 extends Phase

class CommonCoin extends Actor {
  val rnd = new Random(System.currentTimeMillis())
  def receive = {
    case Release => sender ! Output(rnd.nextBoolean())
  }
}

/**
 * Aka Ben Or (for binary decisions)
 */
class RandomizedBinaryConsensus (ownerProcess: ActorRef) extends Actor with FSM[Phase, Unit] with ActorLogging {
  var allProcs = List.empty[ActorRef]
  var N = allProcs.size; var f = allProcs.size / 2 - 1 // will be redefined when allProcs is init'd
  val beb = context.actorOf(Props.apply(new BestEffortBroadcast(ownerProcess)), "Broadcast")
  var round = 0
  var proposal: Option[Boolean] = None
  var decision: Option[Boolean] = None
  var deliveredDecision = false
  var vall = Map.empty[ActorRef, Option[Boolean]]

  val coin = context.actorOf(Props[CommonCoin], "coin")

  def vallWinner(withMoreThan: Int) = vall.groupBy(_._2).mapValues(_.size) find { case(aValue, votes) => votes > withMoreThan}

  startWith(Phase0, Unit)

  when(Phase0) {
    case Event(i @ Initialize(procs), _) =>
      allProcs = procs
      N = allProcs.size
      f = allProcs.size / 2 - 1
      beb ! i
      stay
    case Event(BinaryPropose(v: Boolean), _) =>
      log.info(s"${ownerProcess.path.name} proposed $v")
      proposal = Some(v)
      round = 1
      beb ! Broadcast(Message(BenOrProposal(phase = 1, round, proposal)))
      goto(Phase1)
  } // there are additional transitions for Phase0 below

  when(Phase1) {
    case Event(Deliver(s, Message(BenOrProposal(ph, rnd, prop), _)), _) if rnd == round =>
      vall = vall.updated(s, prop)
      self ! CheckNext
      stay
    case Event(CheckNext, _) if vall.size > allProcs.size / 2 && decision.isEmpty =>
      vallWinner(withMoreThan = allProcs.size / 2) match {
        case Some((w @ Some(winnerValue), _)) =>
          proposal = w
          log.info(s"${ownerProcess.path.name} finished phase 1 with ${proposal.get}")
        case _ =>
          proposal = None
          log.info(s"${ownerProcess.path.name} finished phase 1 with no proposal")
      }
      vall = Map.empty
      beb ! Broadcast(Message(BenOrProposal(phase = 2, round, proposal)))
      goto(Phase2)
  }

  when(Phase2) {
    case Event(Deliver(s, Message(BenOrProposal(ph, rnd, prop), _)), _) if rnd == round =>
      vall = vall.updated(s, prop)
      self ! CheckNext
      stay
    case Event(CheckNext, _) if vall.size >= N - f && decision.isEmpty =>
      coin ! Release
      goto(Phase0)
  }

  when(Phase0) {
    case Event(Output(coinOutput), _) =>
      vallWinner(withMoreThan = f) match {
        case Some((w @ Some(winningValue), _)) =>
          decision = w
          log.info(s"${ownerProcess.path.name} decided in $round with ${winningValue}")
          beb ! Broadcast(Message(BinaryDecide(winningValue)))
          stay
        case _ =>
          vall.find { case(_, aValue) => aValue.isDefined } match {
            case Some((_, conformToValue @ Some(aValue))) =>
              proposal = conformToValue
            case _ => proposal = Some(coinOutput)
          }
          vall = Map.empty
          round += 1
          log.info(s"${ownerProcess.path.name} advancing to round $round with ${proposal.get}")
          beb ! Broadcast(Message(BenOrProposal(phase = 1, round, proposal)))
          goto(Phase1)
      }
  }

  whenUnhandled {
    case Event(Deliver(s, Message(d @ BinaryDecide(newDecision), _)), _) if !deliveredDecision =>
      log.info(s"${ownerProcess.path.name} decided $newDecision")
      decision = Some(newDecision)
      context.parent ! d
      deliveredDecision = true
      stay
  }

  initialize()
}
