import akka.actor._
import failuredetector.EventuallyPerfectFailureDetector
import links.LinkCommon._
import links.PerfectPointToPointLink
import failuredetector.FailureDetectorCommon._

case object PutOnLink
case class AddBuddy(buddy: ActorRef)

class Playa extends Actor with ActorLogging {
  val link = context.actorOf(Props[PerfectPointToPointLink], "PerfectLink")
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
      val detector = context.actorOf(Props[EventuallyPerfectFailureDetector], "FailureDetector")
      detector ! Initialize(all)
    case Suspect(something) =>
      log.info(s"Got a suspect: $something!")
    case Restore(something) =>
      log.info(s"Never mind, $something not suspected anymore!")
  }
}

class Main extends Actor {
  val system = ActorSystem("DistributedSystem")
  val playaOne = system.actorOf(Props[Playa], "Jose")
  val playaTwo = system.actorOf(Props[Playa], "Don")
  //  playaOne ! Send(playaTwo, Message(""))
  playaOne ! Initialize(List(playaOne, playaTwo))
  playaTwo ! Initialize(List(playaOne, playaTwo))

  def receive = {
    case _ =>
    //      context.stop(self)
  }
}