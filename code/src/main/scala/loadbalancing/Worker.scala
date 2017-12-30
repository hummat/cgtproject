package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorRef}
import supervisory.{Action, Reward, State}

import scala.concurrent.duration._

/** This does the policy and loadbalancing stuff */
case class Worker() {
  // do we need a routeQ? Just handle immediately??
  // Should routing cost 0 service time? I think yes.
//  var routeQ= Queue.empty[Task]
//  def enqueue(task: Task) = routeQ.enqueue(task)

  var procQ= List.empty[Task]

  def serviceTime = procQ.map(task => task.s).sum.toDouble / procQ.length

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

}

/** This does the actor stuff */
trait WorkerActor extends Actor with ActorLogging {

  // tasker is the arbitrary task creator
  val tasker = context.system.actorSelection("/user/tasker")

  // Initial Placeholder values
  // Not sure if having the 'experience' like this makes sense
  var step: Integer = 0
  var state: State = ProcessLength(0)
  var action: Action = Process
  var next: State = ProcessLength(0)
  var reward: Reward = InverseLength(0)

  val worker = Worker()

  context.system.scheduler.schedule(
    initialDelay = 1 seconds, interval = 1 seconds, self, Tick
  )

  def receive = {
    case Tick => {
      if (worker hasWork) worker work
      if (worker isDone) tasker ! (worker completeTask)
    }
    case task: Task => {
      // Decide to route or process here
      worker.takeNewTask(task) // for now always process
    }
    case msg => log.info("Worker Fallthrough")
  }
}

// Load-Balancing Messages
case class Task(id: String, s: Integer, c: Integer)
object Tick
case class Service(s: Integer) // sent during a route

// Load-Balancing Implementations of State, Action, Reward
case class ProcessLength(length: Integer) extends State
case class InverseLength(reward: Double) extends Reward
case class  Route(neighbor: ActorRef) extends Action
object Process extends Action
