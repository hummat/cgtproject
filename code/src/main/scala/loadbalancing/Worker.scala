package loadbalancing

import akka.actor.{Actor, ActorLogging}
import supervisory.{Reward, State, Action}

/** This does the loadbalancing stuff */
case class Worker() {

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
  }

}

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