package supervisory

import akka.actor.{Actor, ActorLogging, ActorRef}

trait SubordinateActor extends Actor with ActorLogging {

  val supervisor: ActorRef

  val acc = ExperienceAccumulator(115)

  def receive = {
    case experience: Experience => {
      acc.add(experience)
      if (acc isFull) supervisor ! Episode(acc.transfer)
    }
    case msg => log.info("Subordinate Fallthrough")
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

trait State
trait Action
trait Reward
case class Experience(step: Integer,
                      state: State,
                      action: Action,
                      next: State,
                      reward: Reward)
case class Episode(experiences: List[Experience])