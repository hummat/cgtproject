package supervisory

import akka.actor.{Actor, ActorLogging, ActorRef}

trait SubordinateActor extends Actor with ActorLogging {

  val supervisor: ActorRef

  // These feel incorrect
  var step: Integer
  var state: State
  var action: Action
  var next: State
  var reward: Reward

  val acc = ExperienceAccumulator(115)

  def receive = {
    case msg => log.info("Subordinate receive")
    case Share => {
      val experience = Experience(step, state, action, next, reward)
      acc.add(experience)
      if (acc isFull) supervisor ! Episode(acc.transfer)
    }
  }
}

case class ExperienceAccumulator(window: Integer) {
  var experiences = List[Experience]()
  def add(experience: Experience) = experiences ::= experience
  def isFull = experiences.length >= window
  def transfer = {
    val transfer = experiences.take(window)
    experiences = Nil
    transfer
  }
}

object Share

trait State
trait Action
trait Reward
case class Experience(step: Integer,
                      state: State,
                      action: Action,
                      next: State,
                      reward: Reward)
case class Episode(experiences: List[Experience])