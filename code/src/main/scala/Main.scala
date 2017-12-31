import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import loadbalancing.{Task, WorkerActor}
import supervisory.{SubordinateActor, SupervisorActor}

/**
  * Main entry point
  */
object Main extends App {
  import Message._

  val system = ActorSystem("s")

  val graph = List( // vertex -> edges
    1 -> (2,3,4),
    2 -> (1,3,4),
    3 -> (1,2,4),
    4 -> (2,3,4)
  )

  val environment = system.actorOf(Props[Environment],
    "environment")
  val supervisor = system.actorOf(Props[SupervisorNode],
    "supervisor1")
  val worker1 = system.actorOf(
    Props(classOf[WorkerNode], supervisor, List("worker2")),
    "worker1")
  val worker2 = system.actorOf(
    Props(classOf[WorkerNode], supervisor, List("worker1")),
    "worker2")

  worker1 ! Task(id = "1", step = 1, s = 3, c = 0)
  worker1 ! Task(id = "2", step = 1, s = 2, c = 0)
  worker1 ! Task(id = "3", step = 1, s = 3, c = 0)

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

case class Environment() extends Actor with ActorLogging {
  def receive = {
    case task: Task => log.info("Environment received " + task.toString)
  }
}

/** Different types of messages to be sent */
object Message {
  case object BlankMessage
}
