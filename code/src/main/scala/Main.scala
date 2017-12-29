import akka.actor.{ActorRef, ActorSystem, Props}
import loadbalancing.WorkerActor
import supervisory.{SubordinateActor, SupervisorActor}

/**
  * Main entry point
  */
object Main extends App {
  import Message._

  val system = ActorSystem("DynamicColearning")

  val supervisor = system.actorOf(Props[SupervisorNode])
  val worker = system.actorOf(Props(classOf[WorkerNode], supervisor))

  worker ! BlankMessage
  supervisor ! BlankMessage

  system.terminate()
}

/** Concretized worker */
case class WorkerNode(supervisor: ActorRef)
  extends WorkerActor with SubordinateActor {

  override def receive =
    super[WorkerActor].receive andThen super[SubordinateActor].receive
}

/** Concretized supervisor */
case class SupervisorNode() extends SupervisorActor

/** Different types of messages to be sent */
object Message {
  case object BlankMessage
}
