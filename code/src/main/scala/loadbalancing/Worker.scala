package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorSelection}
import supervisory._

import scala.concurrent.duration._

/** This does the policy and loadbalancing stuff */
case class Worker(neighbors: List[ActorSelection]) {

  // do we need a routeQ? Just handle immediately??
  // Should routing cost 0 service time? I think yes.
//  var routeQ= Queue.empty[Task]
//  def enqueue(task: Task) = routeQ.enqueue(task)

  var procQ= List.empty[Task]

  var step = 0 // counts how many actions have been performed

  def serviceTime = procQ.map(task => task.s).sum.toDouble / procQ.length

  def increment = step += 1

  def hasWork = !procQ.isEmpty

  def work = {
    val curr = procQ.head
    procQ = Task(curr.id, curr.s - 1, curr.c) :: procQ.tail
    procQ = procQ.map(task => Task(task.id, task.s, task.c + 1))
  }

  def isDone = procQ.head.s == 0

  def completeTask =
    procQ splitAt 1 match { case (done, rest) => procQ = rest; done }

  def takeNewTask(task: Task) = procQ = (task :: procQ.reverse).reverse

  //TODO: Implement decision-making
  def decideAction = Process
  def decideNeighbor = neighbors.head
}

/** This does the actor stuff */
trait WorkerActor extends Actor with ActorLogging {

  val neighborNames: List[String]
  lazy val neighbors = neighborNames.map(name =>
    context.system.actorSelection(s"/user/$name"))

  // tasker is the arbitrary task creator
  val tasker = context.system.actorSelection("/user/tasker")

  // worker logic
  lazy val worker = Worker(neighbors)

  // processing clock
  context.system.scheduler.schedule(
    initialDelay = 1 seconds, interval = 1 seconds, self, Tick
  )

  def receive = {

    case Tick => {
      if (worker hasWork) worker work
      if (worker isDone) tasker ! (worker completeTask)
    }

    case task: Task => {
      val current = worker.serviceTime
      worker.increment

      if(worker.decideAction == Process) { // Process the task
        worker.takeNewTask(task)
        // Send the reward signal to yourself
        self ! Act(
          worker step, current, Process, worker serviceTime, 1 / current)
      } else { // Route the task
        val neighbor = worker.decideNeighbor
        // Route message retrieves reward signal
        neighbor ! Route(worker step, current)
        // Task message actually routes the Task
        neighbor ! task
      }
    }

    case Route(step, current) =>
      sender ! Act(
        step, current, Route, current, 1 / (worker serviceTime))

    case Act(step, current, action, next, reward) =>
      // Send it over to the supervisory portion
      self ! Experience(
        step, ServiceTime(current), action, ServiceTime(next), InverseService(reward))

    case msg => log.info("Worker Fallthrough")
  }
}

// Load-Balancing Messages
case class Task(id: String, s: Integer, c: Integer)
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
object Route extends Action
object Process extends Action