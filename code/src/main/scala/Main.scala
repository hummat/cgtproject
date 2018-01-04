import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import loadbalancing.{BossActor, Task, Tick, WorkerActor}
import supervisory.{SubordinateActor, SupervisorActor}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Main entry point
  */
object Main extends App {

  val maxSteps = 10000
  val numSupervisors = 1
  val numSubordinates = 10
  val maxBranchingFactor = 3

  val system = ActorSystem("dynamicColearning")
  val environment = system.actorOf(
    Props(classOf[Environment], maxSteps, numSubordinates), "environment")

  println("Begin actor creation")
  Graph(system, numSupervisors, numSubordinates, maxBranchingFactor)
  println("End actor creation")

  environment ! Start
}

case class Graph(system: ActorSystem,
                 numSupervisors: Int,
                 numSubordinates: Int,
                 maxBranchingFactor: Int) {
  val supervisors = (1 to numSupervisors).map(num =>
    system.actorOf(Props[SupervisorNode], s"supervisor$num")).toArray
  val graph = (1 to numSubordinates).map(num => {
    val numNeighbors = Random.nextInt(maxBranchingFactor) + 1
    val neighbors = (1 to numNeighbors).map(i => {
      val neighbor = Random.nextInt(numSubordinates) + 1
      s"worker$neighbor"
    })
    s"worker$num" -> neighbors.toList
  })
  val workers = graph map { case (worker, neighbors) =>
    // Random Supervisor
    val supervisor = supervisors(Random.nextInt(numSupervisors))
    system.actorOf(Props(classOf[WorkerNode], supervisor, neighbors), worker)
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

case class Environment(max: Int, workers: Int)
  extends Actor with ActorLogging {
  var step = 1

  def receive = {
    case Start =>
      context.system.scheduler.schedule(
        initialDelay = 1 seconds, interval = 1 seconds, self, Tick)
    case Tick =>
      if (step > max) Await.ready(context.system.terminate(), Duration.Inf)

      // random policy as default with max serviceTime of 20
      val chosen = Random.nextInt(workers) + 1
      val worker = context.system.actorSelection(s"/user/worker$chosen")
      val serviceTime = Random.nextInt(20) + 1
      worker ! Task(step.toString, step, serviceTime, serviceTime, 0)
      step += 1
    case task: Task => log.info("Environment received " + task.toString)
  }
}

object Start