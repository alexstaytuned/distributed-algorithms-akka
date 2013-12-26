package distributed.links

import org.scalatest._
import akka.actor._
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import distributed.Common._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class PerfectPointToPointLinkTest extends TestKit(ActorSystem("PerfectLinkTest"))
with FunSuite
with BeforeAndAfterAll
with ShouldMatchers
with ImplicitSender {

  test("Perfect Link has to deliver messages correctly") {
    val probe = TestProbe()
    val alice = system.actorOf(Props.apply(new Process(probe.ref)), "alice")
    val bob = system.actorOf(Props.apply(new Process(probe.ref)), "bob")

    val msg = Message("blah", ts = 1l)
    alice ! Send(bob, msg)
    within(300 millis) {
      probe.expectMsg(Deliver(alice, msg))
    }
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }

  class Process(probe: ActorRef) extends Actor {
    val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
    def receive = {
      case s @ Send(dst, msg) =>
        link ! s
      case x =>
        probe forward x
    }
  }

}
