import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import loadbalancing.{BossActor, Task, Tick, WorkerActor}
import supervisory.{SubordinateActor, SupervisorActor}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

import java.io._
//import vegas._

/**
  * Main entry point
  */
object Main extends App {

  // Experiment Parameters
  val filename = "N_9sup_n324_scaled2.csv"
  val maxSteps = 10000
  val numSupervisors = 9
  val numSubordinates = 324
  val window = 25
  val trials = 10
  val noise = 0.0 // wtf is juice???

  // Other Parameters
  val maxBranchingFactor = 10
  val maxServiceTime = 150

  val bw = new BufferedWriter(new FileWriter(new File(filename)))
  bw.write("trial,step,original,complete,window,sups,size\n")
  for (trial <- 1 to trials) {
    val system = ActorSystem("dynamicColearning")

    val environment = system.actorOf(
      Props(classOf[Environment],
        bw,
        maxSteps,
        numSubordinates,
        maxServiceTime,
        filename,
        trial,
        window,
        numSupervisors
      ), "environment")

    // do we want a new graph for each trial??? probably not.
    Graph(system, numSupervisors, numSubordinates, maxBranchingFactor, window)

    environment ! Start

    // Environment will terminate system when at maxSteps
    Await.ready(system.whenTerminated, Duration.Inf)
  }
  bw.close()
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

// gross with so many inputs
case class Environment(bw: BufferedWriter,
                       maxStep: Int,
                       workers: Int,
                       maxServiceTime: Int,
                       filename: String,
                       trial: Int,
                       window: Int,
                       sups: Int)
  extends Actor with ActorLogging {
  var step = 1
  var arr = List.empty[(Int, Int)]

  def receive = {
    case Start =>
      context.system.scheduler.schedule(
        initialDelay = 10 microseconds, interval = 10 microseconds, self, Tick)
    case Tick =>
      val chosen = Random.nextInt(workers) + 1
      val worker = context.system.actorSelection(s"/user/worker$chosen")
      val serviceTime = Random.nextInt(maxServiceTime) + 1
      worker ! Task(step.toString, step, serviceTime, serviceTime, 0)
      step += 1

      // Stop at maxTimeSteps
      if (step > maxStep) {
//        Graph.graph(s"sdiff$trial", arr)
        context.system.terminate()
      }

    case task: Task =>
      //TODO: output tasks received to csv
      val row = s"$trial," +
        s"${task.step}," +
        s"${task.o}," +
        s"${task.c}," +
        s"$window," +
        s"$sups," +
        s"$workers\n"
      bw.write(row)

      log.info(task.toString)
//      arr = (task.step, task.c - task.o) :: arr
  }
}

object Start

//object Graph {
//  def graph(name: String, arr: List[(Int, Int)]): Unit = {
//    val pw = new PrintWriter(new File(s"$name.html"))
//    implicit val renderer = vegas.render.ShowHTML(pw.write)
//    Vegas("Difference")
//      .withXY(arr)
//      .encodeX("x", Quantitative, title="Step")
//      .encodeY("y", Quantitative, title="Diff")
//      .mark(Bar)
//      .show
//    pw.close
//  }
//
//}