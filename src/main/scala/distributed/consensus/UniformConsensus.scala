package distributed.consensus

import akka.actor._
import distributed.Common._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class UniformConsensus(ownerProcess: ActorRef) extends Actor with ActorLogging {
  var allProcs = List.empty[ActorRef]
  val epochChange = context.actorOf(Props.apply(new EpochChange(ownerProcess)), "EpochChange")
  var epochConsensus: ActorRef = _
  def mkEpochConsensus(ts: Long) = context.actorOf(Props.apply(new EpochConsensus(ownerProcess)), s"EpochConsensus.$ts")
  var value: Option[Int] = None
  var proposed = false
  var decided = false
  var ets: Long = 0
  var leader: ActorRef = _
  var newTs: Long = 0
  var newLeader: ActorRef = _

  def receive = {
    case InitializeUniformConsensus(procs: List[ActorRef], initLeader: ActorRef) =>
      allProcs = procs; leader = initLeader
      epochConsensus = mkEpochConsensus(0)
      epochChange ! InitializeEpochChange(allProcs, initLeader, ownerProcess.path.name.size)
      epochConsensus ! InitializeEpochConsensus(allProcs, initLeader, 0, EpochState(0, None))
      context.system.scheduler.schedule(100 millis, 100 millis, self, ProposeIfLeader)
    case Propose(v) =>
      log.info(s"${ownerProcess.path.name} proposed $v")
      value = Some(v)
    case StartEpoch(aNewTs, aNewLeader) =>
      newTs = aNewTs; newLeader = aNewLeader
      log.info(s"${ownerProcess.path.name} starting epoch at $aNewTs with leader ${aNewLeader.path.name}")
      epochConsensus ! Abort
    case Aborted(state) if senderEts(sender) == ets =>
      ets = newTs; leader = newLeader
      proposed = false
      epochConsensus = mkEpochConsensus(ets)
      epochConsensus ! InitializeEpochConsensus(allProcs, leader, ets, state)
    // debug only
    case Aborted(state) =>
      log.info("Got a stale-looking aborted message")
    case ProposeIfLeader if leader == ownerProcess && value.isDefined && ! proposed =>
      proposed = true
      epochConsensus ! Propose(value.get)
    case d @ Decide(v) if senderEts(sender) == ets && ! decided =>
        log.info(s"${ownerProcess.path.name} decided $v")
        decided = true
        context.parent ! d
  }

  def senderEts(sender: ActorRef) = {
    sender.path.name.split("""\.""")(1).toLong
  }
}

case object ProposeIfLeader