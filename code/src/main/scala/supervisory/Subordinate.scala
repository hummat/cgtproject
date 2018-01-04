package supervisory

import akka.actor.{Actor, ActorLogging, ActorRef}

trait SubordinateActor extends Actor with ActorLogging {

  val supervisor: ActorRef
  val window: Int

  val acc = ExperienceAccumulator(window)

  def receive = {
    case experience: Experience =>
      acc.add(experience)
      if (acc isFull) supervisor ! Episode(acc.transfer)
    case Episode(experiences) =>
      self ! Share(experiences)

//    case msg => log.info(s"Subordinate Fallthrough: $msg")
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
case class Share(experiences: List[Experience]) // unnecessary???
