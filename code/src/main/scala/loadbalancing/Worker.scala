package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection}
import supervisory._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

/** This does the policy and loadbalancing stuff */
case class Worker(selfAS: ActorSelection,
                  neighbors: List[ActorSelection],
                  window: Int) {

  var process= List.empty[Task]
  var q = (selfAS :: neighbors).map(neighbor => neighbor -> 0.0).toMap
  val gamma = 0.1
  val tau = 0.1
  val epsilon = 0.1

  var serviceTimes = List.empty[Double]
  var environmentRates = List.empty[Int]
  var agentRates = List.empty[Int]

  def updateQ(actor: ActorSelection, reward: Double) = {
    val current = q.getOrElse(actor, 0.0)
    q = q + (actor -> (current + gamma * (reward - current)))
  }

  def serviceTime = (0.0 /: process) (_ + _.s) / math.max(process.length,1)

  def avgServiceTime = serviceTimes.sum / serviceTimes.length

  def inverseService = if (serviceTime != 0) 1 / serviceTime else 0.0

  // backward finite difference approximation of rate of tasks from environment
  def updateEnvironmentRate(task: Task) =
    environmentRates = (task.step :: environmentRates).take(window)

  def environmentRate = {
//    (environmentRates.head - environmentRates.last) / environmentRates.length
    if (environmentRates.isEmpty) 0.0 else {
      val a = environmentRates.head
      val b = environmentRates.last
      if ((a - b) == 0) 0.0 else 1.0 / (a - b)
//      var a = environmentRates.head
//      1 / ((for (b <- environmentRates.tail) yield {
//        val t = a - b; a = b; t
//      }).sum.toDouble / (environmentRates.length - 1))
//    }
  }

  // backward finite difference approximation of rate of tasks from other agents
  def updateAgentRate(task: Task) =
    agentRates = (task.step :: agentRates).take(window)

  def agentRate = {
//    (agentRates.head - agentRates.last) / agentRates.length
    if (agentRates.isEmpty) 0.0 else {
      val a = agentRates.head
      val b = agentRates.last
      if ((a - b) == 0) 0.0 else 1.0 / (a - b)
//      var a = agentRates.head
//      1 / ((for (b <- agentRates.tail) yield {
//        val t = a - b; a = b; t
//      }).sum.toDouble / (agentRates.length - 1))
    }
  }

  def hasWork = !process.isEmpty

  def work = {
    val curr = process.head
    process = curr.copy(s = curr.s - 1) :: process.tail
    process = process.map(task => task.copy(c = task.c + 1))
  }

  def isDone = process.head.s == 0

  def completeTask =
    process splitAt 1 match { case (done, rest) => process = rest; done }

  def takeNewTask(task: Task) = {
    process = (task :: process.reverse).reverse
    serviceTimes = (serviceTime :: serviceTimes) take window
  }

  def exp(actor: ActorSelection): Double = math.pow(math.E, q(actor) / tau)

  def pr(actor: ActorSelection): Double = exp(actor) / q.keys.map(exp).sum

  def decideNeighbor: ActorSelection ={
    val r = Random nextDouble;
    var sum = 0.0
    for ((actor, q) <- q) yield {
      sum += pr(actor)
      if (r <= sum) return actor
    }
    q.head._1 // this should never happen
  }

  def updateExperiences(experiences: List[Experience]) = {
    experiences foreach {
      case Experience(step, state, Process, next, InverseService(r)) =>
        updateQ(selfAS, r)
      case Experience(step, state, Forward(_, index), next, InverseService(r)) =>
        updateQ(neighbors(index), r)
    }
  }
}

/** This does the actor stuff */
trait WorkerActor extends Actor with ActorLogging {

  val window: Int
  val neighborNames: List[String]
  lazy val neighbors = neighborNames.map(name =>
    context.system.actorSelection(s"/user/$name"))

  // environment is the arbitrary task creator
  val environment = context.system.actorSelection("/user/environment")

  // worker logic
  val selfAS = context.system.actorSelection(self.path)
  lazy val worker = Worker(selfAS, neighbors, window)

  // process clock
  context.system.scheduler.schedule(
    initialDelay = 10 microseconds, interval = 10 microseconds, self, Tick)

  def receive = {

    case Tick =>
      if (worker hasWork) {
        worker work;
        if (worker isDone) {
          environment ! (worker completeTask).head
        }
      }

    case task: Task =>
      val current = worker.serviceTime
      val neighbor = worker.decideNeighbor
      val index = neighbors.indexOf(neighbor)

      if (context.system.actorSelection(sender.path) == environment)
        worker.updateEnvironmentRate(task)
      else worker.updateAgentRate(task)

      if (neighbor == selfAS) { // Process the task
        worker.takeNewTask(task)
        // Send the reward signal to yourself
        self ! Act(
          task step, current, Process, worker serviceTime, worker inverseService)
      } else { // Route the task
        // Task message actually routes the Task
        neighbor ! task
        // Route message retrieves reward signal
        neighbor ! Route(task step, current, index)
      }

    case Route(step, current, index) =>
      sender ! Act(
        step, current, Forward(self, index), current, worker inverseService)

    case Act(step, current, action, next, reward) =>
      // Send it over to the supervisory portion
      worker updateQ(context.system.actorSelection(sender.path), reward)
      self ! Experience(
        step, ServiceTime(current), action, ServiceTime(next), InverseService(reward))

    case Share(experiences) => worker updateExperiences(experiences)

    case LoadRequest => sender ! Load(worker avgServiceTime)
    case NeighborRequest => sender ! Neighbors(worker neighbors)
    case EnvironmentRateRequest => sender ! Rate(worker environmentRate)
    case AgentRateRequest => sender ! Rate(worker agentRate)

//    case msg => log.info("Worker Fallthrough")
  }
}

// Boss Messages
object LoadRequest
case class Load(serviceTime: Double)
object NeighborRequest
case class Neighbors(neighbors: List[ActorSelection])
object EnvironmentRateRequest
case class Rate(rate: Double)
object AgentRateRequest

// Load-Balancing Messages
case class Task(id: String, step: Int, s: Int, o: Int, c: Int)
object Tick
case class Route(step: Integer, serviceTime: Double, index: Integer)
case class Act(step: Integer,
               serviceTime: Double,
               action: Action,
               nextServiceTime: Double,
               reward: Double)

// Load-Balancing Implementations of State, Action, Reward
case class ServiceTime(length: Double) extends State
case class InverseService(reward: Double) extends Reward
case class Forward(actor: ActorRef, index: Integer) extends Action
object Process extends Action