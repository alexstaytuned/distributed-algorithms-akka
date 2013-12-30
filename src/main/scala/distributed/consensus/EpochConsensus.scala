package distributed.consensus

import akka.actor._
import distributed.links.PerfectPointToPointLink
import distributed.Common._
import distributed.broadcast.BestEffortBroadcast

class EpochConsensus (ownerProcess: ActorRef) extends Actor with ActorLogging {
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
    case Deliver(asker, Message(write @ Write(v), _)) =>
      value = Some(v)
      valts = ets
      link ! Send(source = ownerProcess, destination = asker, Message(Accept))
    case Deliver(subordinate, Message(Accept, _)) =>
      accepted += 1
      self ! CheckWrites
    case CheckWrites if accepted > allProcs.size / 2 =>
      accepted = 0
      beb ! Broadcast(Message(Decided(tmpval.get)))
    case Deliver(asker, Message(Decided(v), _)) =>
      context.parent ! Decide(v)
    case Abort =>
      context.parent ! Aborted(EpochState(valts, value))
  }

  def highest = states.maxBy { case (_, EpochState(ts, _)) => ts }._2 /* state */
}


case object Accept
case object CheckStateReads
case object CheckWrites
case class Decided(v: Int)
case class Write(value: Int)
