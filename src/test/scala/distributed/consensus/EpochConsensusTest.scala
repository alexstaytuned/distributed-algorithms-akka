package distributed.consensus

import org.scalatest._
import akka.actor._
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import distributed.Common._
import scala.concurrent.duration._
import distributed.TestCommon.{Resurrect, Die}
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class EpochConsensusTest extends TestKit(ActorSystem("EpochConsensusTest"))
with FunSuite
with BeforeAndAfterAll
with ShouldMatchers
with ImplicitSender {

  val rnd = new Random

  test("Epoch consensus is reached") {
    val probe = TestProbe()
    val alice = system.actorOf(Props.apply(new Process(probe.ref)), "alice")
    val bob = system.actorOf(Props.apply(new Process(probe.ref)), "bob")
    val charlie = system.actorOf(Props.apply(new Process(probe.ref)), "charlie")
    val dan = system.actorOf(Props.apply(new Process(probe.ref)), "dan")
    val procs = List(alice, bob, charlie, dan)
    var i = 1
    procs.foreach { proc =>
      proc ! InitializeEpochChange(procs, alice, i)
      i += 1
    }

    val msg = probe.receiveOne(200 millis)
    var leader: ActorRef = alice
    var epoch: Long = -1
    msg match {
      case StartEpoch(ts, l) =>
        ts should be > 0l
        leader = l
        epoch = ts
    }
    probe.receiveN(3)

    procs.foreach { proc =>
      proc ! InitializeEpochConsensus(procs, leader, epoch, EpochState(0, None))
    }

    val decisions = probe.receiveN(4)
    decisions.foreach {
        case Decide(value) =>
        case _ => fail("Not a correct message")
    }
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }

  class Process(probe: ActorRef) extends Actor {
    var changer = context.actorOf(Props.apply(new EpochChange(self)), "EpochChange")
    var consensus = context.actorOf(Props.apply(new EpochConsensus(self)), "EpochConsensus")
    def receive = {
      case Die => context.stop(changer)
      case e @ InitializeEpochChange(procs, initLeader, rank) =>
        changer ! e
      case c @ InitializeEpochConsensus(procs, leader, ts, state) =>
        consensus ! c
        consensus ! Propose(rnd.nextInt(15))
      case x =>
        probe forward x
    }
  }

}
