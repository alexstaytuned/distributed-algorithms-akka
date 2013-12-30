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

@RunWith(classOf[JUnitRunner])
class EpochChangeTest extends TestKit(ActorSystem("EpochChangeTest"))
with FunSuite
with BeforeAndAfterAll
with ShouldMatchers
with ImplicitSender {

  test("Epoch change messages deliver correctly") {
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
    var secondEpoch: Long = -1
    msg match {
      case StartEpoch(ts, l) =>
        ts should be > 0l
        leader = l
        epoch = ts
    }

    probe.receiveN(procs.size - 1 /* already received the first one */).foreach {
      case StartEpoch(ts, l) =>
        ts should equal(epoch)
        l should be === leader
    }

    leader ! Die

    probe.receiveN(procs.size - 1 /* one is already dead */).foreach {
      case StartEpoch(ts, l) =>
        ts should be > epoch
        l should not be leader
        secondEpoch = ts
    }
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }

  class Process(probe: ActorRef) extends Actor {
    var changer = context.actorOf(Props.apply(new EpochChange(self)), "EpochChange")
    def receive = {
      case Die => context.stop(changer)
      case e @ InitializeEpochChange(procs, initLeader, rank) =>
        changer ! e
      case x =>
        probe forward x
    }
  }

}
