package distributed.broadcast

import distributed.failuredetector.EventuallyPerfectFailureDetector
import org.scalatest._
import akka.actor._
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import distributed.Common._
import scala.concurrent.duration._
import distributed.Common.Initialize

@RunWith(classOf[JUnitRunner])
class BestEffortBroadcastTest extends TestKit(ActorSystem("BebTest"))
with FunSuite
with BeforeAndAfterAll
with ShouldMatchers
with ImplicitSender {

  test("Best Effort Broadcast delivers") {
    val aProbe = TestProbe()
    val bProbe = TestProbe()
    val cProbe = TestProbe()
    val alice = system.actorOf(Props.apply(new Process(aProbe.ref)), "alice")
    val bob = system.actorOf(Props.apply(new Process(bProbe.ref)), "bob")
    val charlie = system.actorOf(Props.apply(new Process(cProbe.ref)), "charlie")

    val procs = List(alice, bob, charlie)
    val msg = Message("blah", ts = 1l)
    alice ! Initialize(procs)
    bob ! Initialize(procs)
    charlie ! Initialize(procs)

    alice ! Broadcast(msg)

    aProbe expectMsg Deliver(alice, msg)
    bProbe expectMsg Deliver(alice, msg)
    cProbe expectMsg Deliver(alice, msg)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }

  class Process(probe: ActorRef) extends Actor {
    val beb = context.actorOf(Props[BestEffortBroadcast], "Broadcast")
    var processes = List.empty[ActorRef]
    def receive = {
      case i @ Initialize(procs) =>
        beb ! i
        processes = procs
      case Broadcast(m) =>
        beb ! Broadcast(m)
      case x =>
        probe forward x
    }
  }

}
