import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import loadbalancing.{Task, WorkerActor}
import supervisory.{SubordinateActor, SupervisorActor}

/**
  * Main entry point
  */
object Main extends App {
  import Message._

  val system = ActorSystem("s")

  val tasker = system.actorOf(Props[Tasker],
    "tasker")
  val supervisor = system.actorOf(Props[SupervisorNode],
    "supervisor1")
  val worker1 = system.actorOf(
    Props(classOf[WorkerNode], supervisor, List("worker2")),
    "worker1")
  val worker2 = system.actorOf(
    Props(classOf[WorkerNode], supervisor, List("worker1")),
    "worker2")

//  worker1 ! BlankMessage
//  supervisor ! BlankMessage

  worker1 ! Task("1", 3, 0)
  worker1 ! Task("2", 2, 0)
  worker1 ! Task("3", 3, 0)

  Thread.sleep(10000)
  system.terminate()
}

/** Concretized worker */
case class WorkerNode(supervisor: ActorRef, neighborNames: List[String])
  extends WorkerActor with SubordinateActor {

  override def receive: Receive =
    super[WorkerActor].receive andThen super[SubordinateActor].receive
}

/** Concretized supervisor */
case class SupervisorNode() extends SupervisorActor

case class Tasker() extends Actor with ActorLogging {
  def receive = {
    case task: Task => log.info("Tasker received " + task.toString)
  }
}

/** Different types of messages to be sent */
object Message {
  case object BlankMessage
}
