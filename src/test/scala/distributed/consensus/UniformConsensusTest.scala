package distributed.consensus
import org.scalatest._
import akka.actor._
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import distributed.Common._
import scala.util.Random
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class UniformConsensusTest extends TestKit(ActorSystem("UniformConsensusTest"))
with FunSuite
with BeforeAndAfterAll
with ShouldMatchers
with ImplicitSender {

  val rnd = new Random

  test("Uniform consensus is reached") {
    val probe = TestProbe()
    // names must be different length because this is the way rank is determined
    val alice = system.actorOf(Props.apply(new Process(probe.ref)), "alice")
    val bob = system.actorOf(Props.apply(new Process(probe.ref)), "bob")
    val charlie = system.actorOf(Props.apply(new Process(probe.ref)), "charlie")
    val dan = system.actorOf(Props.apply(new Process(probe.ref)), "danielle")
    val procs = List(alice, bob, charlie, dan)

    alice   ! InitializeUniformConsensus(procs, alice)
    bob     ! InitializeUniformConsensus(procs, alice)
    charlie ! InitializeUniformConsensus(procs, alice)
    dan     ! InitializeUniformConsensus(procs, alice)

    Thread.sleep(5000)

    val msg = probe receiveOne(1000 millis)
    val decision = msg match {
      case Decide(v) => v
      case m => fail(s"wrong message type, expected a Decide, got $m")
    }

    val decisions = probe.receiveWhile[Unit]() {
      case Decide(v) => v should be(decision); v
      case m => fail(s"wrong message type, expected a Decide, got $m")
    }

    decisions.size should be(3)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }

  class Process(probe: ActorRef) extends Actor with ActorLogging {
    var consensus = context.actorOf(Props.apply(new UniformConsensus(self)), "UniformConsensus")
    def receive = {
      case c @ InitializeUniformConsensus(procs, leader) =>
        consensus ! c
        consensus ! Propose(rnd.nextInt(15))
      case x =>
        probe forward x
    }
  }

}