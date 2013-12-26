package distributed

import akka.actor._
import distributed.links.PerfectPointToPointLink
import distributed.links.LinkCommon._
import distributed.failuredetector.FailureDetectorCommon._
import distributed.leader.EventualLeaderDetector
import distributed.leader.LeaderCommon.Trust

case object PutOnLink
case class Initialize(allProcs: List[ActorRef])

class Playa extends Actor with ActorLogging {
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
//  val detector = context.actorOf(Props[EventuallyPerfectFailureDetector], "FailureDetector")
  val leader = context.actorOf(Props[EventualLeaderDetector], "LeaderDetector")

  var nufSaid = 0
  def receive = {
    case s @ Send(friend, _) => link ! Send(friend, Message("hellooo from " + self.path.name))
    case Deliver(from, msg) =>
      println(self.path.name +  " got: " + msg)
      if(nufSaid < 3) {
        link ! Send(from, Message("hi you from " + self.path.name + " # " + nufSaid))
        nufSaid += 1
      }
    case Initialize(all) =>
//      detector ! Initialize(all)
      leader ! Initialize(all)
    case Suspect(something) =>
      log.info(s"Got a suspect: $something!")
    case Restore(something) =>
      log.info(s"Never mind, $something not suspected anymore!")
    case Trust(someone) =>
      log.info("Trusting: " + someone)
  }
}

object Main extends App {
  val system = ActorSystem("DistributedSystem")
  val playaOne = system.actorOf(Props[Playa], "Jose")
  val playaTwo = system.actorOf(Props[Playa], "Don")
  playaOne ! Send(playaTwo, Message(""))
  playaOne ! Initialize(List(playaOne, playaTwo))
  playaTwo ! Initialize(List(playaOne, playaTwo))
  Thread.sleep(10000)


  system.shutdown()
//
//  def receive = {
//    case _ =>
//    //      context.stop(self)
//  }
}