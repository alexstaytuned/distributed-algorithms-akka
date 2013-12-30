package distributed.consensus

import akka.actor._
import distributed.links.PerfectPointToPointLink
import distributed.Common._
import distributed.broadcast.BestEffortBroadcast

/**
 * This is an implementation of Paxos
 * @param ownerProcess
 */
class EpochConsensus(ownerProcess: ActorRef) extends Actor with ActorLogging {
  var allProcs = List.empty[ActorRef]
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
  val beb = context.actorOf(Props.apply(new BestEffortBroadcast(ownerProcess)), "Broadcast")
  var leader: ActorRef = _
  var ets: Long = _
  var valts: Long = _
  var value: Option[Int] = None
  var accepted = 0
  var states: Map[ActorRef, EpochState] = Map.empty
  var tmpval: Option[Int] = None

  def receive = {
    case InitializeEpochConsensus(procs, _leader, _ts, state) =>
      allProcs = procs; leader = _leader; ets = _ts; valts = state.ts; value = state.value
      beb ! Initialize(allProcs)
    case Propose(_value) if ownerProcess == leader =>
      tmpval = Some(_value)
      beb ! Broadcast(Message(EpochConsensusRead))
      log.info(s"${ownerProcess.path.name} asking for opinions")
    case Deliver(asker, Message(EpochConsensusRead, _)) =>
      link ! Send(source = ownerProcess, destination = asker, Message(EpochState(valts, value)))
    case Deliver(subordinate, Message(state @ EpochState(ts, v), _)) if ownerProcess == leader =>
      states = states.updated(subordinate, state)
      self ! CheckStateReads
    case CheckStateReads if states.size > allProcs.size / 2 && ownerProcess == leader =>
      val maxTsState = highest
      if(maxTsState.value.isDefined) tmpval = maxTsState.value
      states = Map.empty
      beb ! Broadcast(Message(Write(tmpval.get)))
    case CheckStateReads => log.info(s"${ownerProcess.path.name}: not enough reads yet (${states.size} out of ${allProcs.size / 2 + 1}}), waiting")
    case Deliver(asker, Message(write @ Write(v), _)) =>
      value = Some(v)
      valts = ets
      link ! Send(source = ownerProcess, destination = asker, Message(Accept))
    case Deliver(subordinate, Message(Accept, _)) =>
      accepted += 1
      self ! CheckWrites
    case CheckWrites if accepted > allProcs.size / 2 =>
      accepted = 0
      log.info(s"${ownerProcess.path.name}: broadcasting the decision")
      beb ! Broadcast(Message(Decided(tmpval.get)))
    case CheckWrites => log.info(s"${ownerProcess.path.name}: not enough accepts yet (${accepted} out of ${allProcs.size / 2 + 1}}), waiting")
    case Deliver(asker, Message(Decided(v), _)) =>
      log.info(s"${ownerProcess.path.name}: got a broadcast of a decision from ${asker.path.name}")
      context.parent ! Decide(v)
    case Abort =>
      log.info(s"Epoch $ets is aborted")
      context.parent ! Aborted(EpochState(valts, value))
      context.stop(self)
  }

  def highest = states.maxBy { case (_, EpochState(ts, _)) => ts }._2 /* state */
}


case object Accept
case object CheckStateReads
case object CheckWrites
case class Decided(v: Int)
case class Write(value: Int)
