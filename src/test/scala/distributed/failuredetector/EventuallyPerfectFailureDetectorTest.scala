package distributed.failuredetector

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
class EventuallyPerfectFailureDetectorTest  extends TestKit(ActorSystem("EPFDTest"))
with FunSuite
with BeforeAndAfterAll
with ShouldMatchers
with ImplicitSender {

  test("Eventually Perfect Failure Detector has to suspect nodes correctly") {
    val probe = TestProbe()
    val alice = system.actorOf(Props.apply(new Process(probe.ref)), "alice")
    val bob = system.actorOf(Props.apply(new Process(probe.ref)), "bob")

    val procs = List(alice, bob)
    alice ! Initialize(procs)
    bob ! Initialize(procs)
    alice ! Die
    probe.expectMsg(Suspect(alice))
    alice ! Resurrect
    probe.expectMsg(Restore(alice))
    bob ! Die
    probe.expectMsg(Suspect(bob))
    bob ! Resurrect
    probe.expectMsg(Restore(bob))
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }

  case object Die
  case object Resurrect

  class Process(probe: ActorRef) extends Actor {
    def initDetector = context.actorOf(Props[EventuallyPerfectFailureDetector], "Detector")
    var detector = initDetector
    var processes = List.empty[ActorRef]
    def receive = {
      case i @ Initialize(procs) =>
        detector ! i
        processes = procs
      case Die => context.stop(detector)
      case Resurrect =>
        detector = initDetector
        detector ! Initialize(processes)
      case x =>
        probe forward x
    }
  }

}
