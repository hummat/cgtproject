import akka.actor.{ActorSystem, Props}
import loadbalancing.Worker
import supervisory.{Subordinate, Supervisor}

/**
  * Main entry point
  */
object Main extends App {
  import Message._

  val system = ActorSystem("DynamicColearning")

  val worker = system.actorOf(Props[WorkerNode])
  val supervisor = system.actorOf(Props[SupervisorNode])

  worker ! BlankMessage
  supervisor ! BlankMessage

  system.terminate()
}

/** Concretized worker */
case class WorkerNode() extends Worker with Subordinate {
  override def receive =
    super[Worker].receive andThen super[Subordinate].receive
}

/** Concretized supervisor */
case class SupervisorNode() extends Supervisor

/** Different types of messages to be sent */
object Message {
  case object BlankMessage
}
