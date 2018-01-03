import Main.supervisor
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import loadbalancing.{BossActor, Task, WorkerActor}
import supervisory.{SubordinateActor, SupervisorActor}

/**
  * Main entry point
  */
object Main extends App {

  val system = ActorSystem("dynamicColearning")

  val graph = Map( // vertex -> edges
    1 -> List(2,3,4),
    2 -> List(1,3,4),
    3 -> List(1,2,4),
    4 -> List(2,3,4)
  )




  val environment = system.actorOf(Props[Environment],
    "environment")
  val supervisor = system.actorOf(Props[SupervisorNode],
    "supervisor1")
  var workers:List[ActorRef] = getListWorker(graph, supervisor)

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

  def getListWorker(graph: Map[Int, List[Int]], supervisor: ActorRef): List[ActorRef] = {
    var workers: List[ActorRef] = List()
    for(node <- graph){
      val neighbours = node._2.map(neighbour=> getWorkerName(neighbour))
      val name = getWorkerName(node._1)
      val worker = system.actorOf(
        Props(classOf[WorkerNode], supervisor, neighbours),
        name)
      workers ::= worker
    }
    workers
  }

  def getWorkerName(node: Int): String ={
    s"worker$node"
  }

}

/** Concretized worker */
case class WorkerNode(supervisor: ActorRef, neighborNames: List[String])
  extends WorkerActor with SubordinateActor {

  override def receive: Receive =
    super[WorkerActor].receive orElse super[SubordinateActor].receive
}

/** Concretized supervisor */
case class SupervisorNode() extends BossActor with SupervisorActor {

  override def receive: Receive =
    super[BossActor].receive orElse super[SupervisorActor].receive
}

case class Environment() extends Actor with ActorLogging {
  def receive = {
    case task: Task => log.info("Environment received " + task.toString)
  }
}