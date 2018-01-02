package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection}
import supervisory._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

/** This does the policy and loadbalancing stuff */
case class Worker(selfAS: ActorSelection, neighbors: List[ActorSelection]) {

  // need to "anonymize" neighbors to allow for experience integration

  var process= List.empty[Task]
  var q = (selfAS :: neighbors).map(neighbor => neighbor -> 0.0).toMap
  val gamma = 0.1
  val tau = 1

  var environmentTask = Task("environmentTask", 0, 0, 0)
  var environmentRate = 0
  var agentTask = Task("agentTask", 0, 0, 0)
  var agentRate = 0

  def updateQ(actor: ActorSelection, reward: Double) = {
    val current = q.getOrElse(actor, 0.0)
    q = q + (actor -> (current + gamma * (reward - current)))
  }

  def serviceTime = (0.0 /: process) (_ + _.s) / process.length

  // backward finite difference approximation of rate of tasks from environment
  def updateEnvironmentRate(task: Task) = {
    environmentRate = 1 / (task.step - environmentTask.step)
    environmentTask = task
  }

  // backward finite difference approximation of rate of tasks from other agents
  def updateAgentRate(task: Task) = {
    agentRate = 1 / (task.step - agentTask.step)
    agentTask = task
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

  def takeNewTask(task: Task) = process = (task :: process.reverse).reverse

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
    // how to integrate others' experiences???

  }
}

/** This does the actor stuff */
trait WorkerActor extends Actor with ActorLogging {

  val neighborNames: List[String]
  lazy val neighbors = neighborNames.map(name =>
    context.system.actorSelection(s"/user/$name"))

  // environment is the arbitrary task creator
  val environment = context.system.actorSelection("/user/environment")

  // worker logic
  val selfAS = context.system.actorSelection(self.path)
  lazy val worker = Worker(selfAS, neighbors)

  // process clock
  context.system.scheduler.schedule(
    initialDelay = 1 seconds, interval = 1 seconds, self, Tick
  )

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

      if (sender.path == environment.anchorPath)
        worker.updateEnvironmentRate(task)
      else worker.updateAgentRate(task)

      if (neighbor == selfAS) { // Process the task
        worker.takeNewTask(task)
        // Send the reward signal to yourself
        self ! Act(
          task step, current, Process, worker serviceTime, 1 / current)
      } else { // Route the task
        // Route message retrieves reward signal
        neighbor ! Route(task step, current)
        // Task message actually routes the Task
        neighbor ! task
      }

    case Route(step, current) =>
      sender ! Act(
        step, current, Forward(self), current, 1 / (worker serviceTime))

    case Act(step, current, action, next, reward) =>
      // Send it over to the supervisory portion
      worker updateQ(context.system.actorSelection(sender.path), reward)

      self ! Experience(
        step, ServiceTime(current), action, ServiceTime(next), InverseService(reward))

    case Share(experiences) =>
      worker updateExperiences(experiences)

    case LoadRequest => sender ! Load(worker serviceTime)
    case NeighborRequest => sender ! Neighbors(worker neighbors)
    case EnvironmentRateRequest => sender ! Rate(worker environmentRate)
    case AgentRateRequest => sender ! Rate(worker agentRate)

    case msg => log.info("Worker Fallthrough")
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
case class Task(id: String, step: Integer, s: Integer, c: Integer)
object Tick
case class Route(step: Integer, serviceTime: Double)
case class Act(step: Integer,
               serviceTime: Double,
               action: Action,
               nextServiceTime: Double,
               reward: Double)

// Load-Balancing Implementations of State, Action, Reward
case class ServiceTime(length: Double) extends State
case class InverseService(reward: Double) extends Reward
case class Forward(actor: ActorRef) extends Action
object Process extends Action