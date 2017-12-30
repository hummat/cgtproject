package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection}
import supervisory._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/** This does the policy and loadbalancing stuff */
case class Worker(neighbors: List[ActorSelection]) {

  // do we need a routeQ? Just handle immediately??
  // Should routing cost 0 service time? I think yes.
//  var routeQ= Queue.empty[Task]
//  def enqueue(task: Task) = routeQ.enqueue(task)

  var q = collection.mutable.Map.empty[ActorRef, Double]
  var timesSelected = collection.mutable.Map.empty[ActorRef, Double]

  def updateQ(actor: ActorRef, reward: Double) = {
    if(timesSelected.contains(actor))
      timesSelected += actor -> (timesSelected(actor) +  1);
    else
      timesSelected += actor -> 1;

    var qCurrentValue = 0.0;
    if(q.contains(actor))
      qCurrentValue = q(actor);

    val updated = qCurrentValue + ((reward - qCurrentValue) / timesSelected(actor))
    q += actor -> updated
  }

  var process= List.empty[Task]

  var step = 0 // counts how many actions have been performed

  def serviceTime = (0.0 /: process) (_ + _.s) / process.length
//  def serviceTime = process.map(task => task.s).sum.toDouble / process.length

  def increment = step += 1

  def hasWork = !process.isEmpty

  def work = {
    val curr = process.head
    process = Task(curr.id, curr.s - 1, curr.c) :: process.tail
    process = process.map(task => Task(task.id, task.s, task.c + 1))
  }

  def isDone = process.head.s == 0

  def completeTask =
    process splitAt 1 match { case (done, rest) => process = rest; done }

  def takeNewTask(task: Task) = process = (task :: process.reverse).reverse

  //TODO: Implement decision-making
  def decideAction = Process
  def decideNeighbor: ActorRef ={
    val probabilities = getProbabilities;
    println(probabilities)
    val random = new scala.util.Random;
    val randomNum = random nextDouble;
    var sum = 0.0;
    for ((key ,value) <- probabilities) {
      sum =  sum + value;
      if (randomNum <= sum) {
        return key;
      }
    }
    return ActorRef.noSender;
  }

  def getProbabilities: collection.mutable.Map[ActorRef, Double] = {
    val estimatedExponential = collection.mutable.Map.empty[ActorRef, Double]
    var sum = 0.0;
    var i = 0
    for ((key ,value) <- q) {
      estimatedExponential(key) = scala.math.exp((value / 1.0)); //tau = 1.0
      sum = sum + estimatedExponential(key);
    }

    return estimatedExponential map {case (key, value) => (key, value /sum)}
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
  lazy val worker = Worker(neighbors)

  // process clock
  context.system.scheduler.schedule(
    initialDelay = 1 seconds, interval = 1 seconds, self, Tick
  )

  def receive = {

    case Tick => {
      if (worker hasWork) {
        worker work;
        if (worker isDone) {
          environment ! (worker completeTask).head
        }
      }
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
      worker updateQ(sender, reward)

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