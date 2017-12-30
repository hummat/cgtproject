package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorRef}
import supervisory._

import scala.concurrent.duration._

/** This does the policy and loadbalancing stuff */
case class Worker() {
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

}

/** This does the actor stuff */
trait WorkerActor extends Actor with ActorLogging {

  // tasker is the arbitrary task creator
  val tasker = context.system.actorSelection("/user/tasker")

  // Initial Placeholder values
  // Not sure if having the 'experience' like this makes sense
  var step: Integer = 0
  var state: State = ServiceTime(0)
  var action: Action = Process
  var next: State = ServiceTime(0)
  var reward: Reward = InverseService(0)

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
      val current = worker.serviceTime
      worker.increment

      //TODO: Decide to route or process here
      if(true) { //for now always Process
        worker.takeNewTask(task)
        self ! Act(worker step,
          current,
          Process,
          worker serviceTime,
          1 / current
        )
      } else {
//        other ! Route(worker step, current)
      }
    }
    // for now this just handles Process actions
    case Act(step, current, action, next, reward) => {
      // Send it over to the supervisory portion
      self ! Experience(step,
        ServiceTime(current),
        action,
        ServiceTime(next),
        InverseService(reward)
      )
    }
    case msg => log.info("Worker Fallthrough")
  }
}

// Load-Balancing Messages
case class Task(id: String, s: Integer, c: Integer)
object Tick
//case class Route(step: Integer, serviceTime: Double)
// sent during a route
case class Act(step: Integer,
               serviceTime: Double,
               action: Action,
               nextServiceTime: Double,
               reward: Double)

// Load-Balancing Implementations of State, Action, Reward
case class ServiceTime(length: Double) extends State
case class InverseService(reward: Double) extends Reward
case class Route(neighbor: ActorRef) extends Action
object Process extends Action
