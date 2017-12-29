package loadbalancing

import akka.actor.{Actor, ActorLogging}
import supervisory.{Action, Reward, State}

import scala.collection.mutable

/** This does the policy and loadbalancing stuff */
case class Worker() {
  var routingQueue = mutable.Queue.empty[Task]
  var processingQueue = mutable.Queue.empty[Task]
  def enqueue(task: Task) = routingQueue.enqueue(task)

  def clock: Unit = {
    var step = 0
    while(true) {
      // routing and processing decisions???

      Thread.sleep(1000)
      step += 1
    }
  }

}

/** This does the actor stuff */
trait WorkerActor extends Actor with ActorLogging {
  import States._
  import Actions._
  import Rewards._

  var step: Integer = 0
  var state: State = State1
  var action: Action = Action1
  var next: State = State2
  var reward: Reward = Reward1

  val worker = Worker()

  def receive = {
    case msg => log.info("Worker receive")
    case task: Task => worker.enqueue(task)
    // case share: Share => incorporate shared info from supervisor
  }

}

case class Task(s: Integer)

//Stubs (definitely not correct)
object States {
  case object State1 extends State
  case object State2 extends State
}

object Actions {
  case object Action1 extends Action
  case object Action2 extends Action
}

object Rewards {
  case object Reward1 extends Reward
  case object Reward2 extends Reward
}