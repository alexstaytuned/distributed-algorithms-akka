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
import distributed.Common.InitializeUniformConsensus
import distributed.Common.Decide
import distributed.Common.Propose

@RunWith(classOf[JUnitRunner])
class RandomizedBinaryConsensusTest extends TestKit(ActorSystem("RandomizedBinaryConsensusTest"))
with FunSuite
with BeforeAndAfterAll
with ShouldMatchers
with ImplicitSender {

  val rnd = new Random

  test("Uniform randomized binary consensus is reached") {
    val probe = TestProbe()
    val alice = system.actorOf(Props.apply(new Process(probe.ref)), "alice")
    val bob = system.actorOf(Props.apply(new Process(probe.ref)), "bob")
    val charlie = system.actorOf(Props.apply(new Process(probe.ref)), "charlie")
    val dan = system.actorOf(Props.apply(new Process(probe.ref)), "danielle")
    val procs = List(alice, bob, charlie, dan)

    alice   ! Initialize(procs)
    bob     ! Initialize(procs)
    charlie ! Initialize(procs)
    dan     ! Initialize(procs)

    val msg = probe receiveOne(5000 millis)
    val decision = msg match {
      case BinaryDecide(v) => v
      case m => fail(s"wrong message type, expected a BinaryDecide, got $m")
    }

    val decisions = probe.receiveWhile[Unit]() {
      case BinaryDecide(v) => v should be(decision); v
      case m => fail(s"wrong message type, expected a Decide, got $m")
    }

    decisions.size should be(3)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }

  class Process(probe: ActorRef) extends Actor with ActorLogging {
    var consensus = context.actorOf(Props.apply(new RandomizedBinaryConsensus(self)), "RndBinaryConsensus")
    def receive = {
      case c @ Initialize(procs) =>
        consensus ! c
        consensus ! BinaryPropose(rnd.nextBoolean())
      case x =>
        probe forward x
    }
  }

}