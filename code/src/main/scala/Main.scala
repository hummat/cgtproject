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

  // Experiment Parameters
  val maxSteps = 10000
  val numSupervisors = 1
  val numSubordinates = 10
  val window = 10
  val trials = 10
  val noise = 0.0 // wtf is juice???

  // Other Parameters
  val maxBranchingFactor = 3
  val maxServiceTime = 20

  for (trial <- 1 to trials) {
    val system = ActorSystem("dynamicColearning")

    val environment = system.actorOf(
      Props(classOf[Environment], maxSteps, numSubordinates, maxServiceTime),
      "environment")

    Graph(system, numSupervisors, numSubordinates, maxBranchingFactor, window)

    environment ! Start

    // Environment will terminate system when at maxSteps
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

case class Graph(system: ActorSystem,
                 numSupervisors: Int,
                 numSubordinates: Int,
                 maxBranchingFactor: Int,
                 window: Int) {
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
    if (numSupervisors != 0) {
      val supervisor = supervisors(Random.nextInt(numSupervisors))
      system.actorOf(
        Props(classOf[WorkerNode], supervisor, neighbors, window),
        worker)
    } else {
      system.actorOf(
        Props(classOf[BaselineWorkerNode], neighbors, window), worker)
    }
  }
}

/** Concretized worker */
case class WorkerNode(supervisor: ActorRef,
                      neighborNames: List[String],
                      window: Int)
  extends WorkerActor with SubordinateActor {

  override def receive: Receive =
    super[WorkerActor].receive orElse super[SubordinateActor].receive
}

/** Baseline Concretized Worker **/
case class BaselineWorkerNode(neighborNames: List[String],
                              window: Int) extends WorkerActor

/** Concretized supervisor */
case class SupervisorNode() extends BossActor with SupervisorActor {

  override def receive: Receive =
    super[BossActor].receive orElse super[SupervisorActor].receive
}

case class Environment(max: Int, workers: Int, maxServiceTime: Int)
  extends Actor with ActorLogging {
  var step = 1

  def receive = {
    case Start =>
      context.system.scheduler.schedule(
        initialDelay = 1 seconds, interval = 1 seconds, self, Tick)
    case Tick =>
      // random policy as default with max serviceTime of 20
      val chosen = Random.nextInt(workers) + 1
      val worker = context.system.actorSelection(s"/user/worker$chosen")
      val serviceTime = Random.nextInt(maxServiceTime) + 1
      worker ! Task(step.toString, step, serviceTime, serviceTime, 0)
      step += 1

      // Stop at maxTimeSteps
      if (step > max) context.system.terminate()

    case task: Task =>
      //TODO: output tasks received to csv
      log.info("Environment received " + task.toString)
  }
}

object Start