package supervisory

import akka.actor.{Actor, ActorLogging}

/** This composes the supervising computation */
case class Supervisor() {

}

/** This does the actor stuff */
trait SupervisorActor extends Actor with ActorLogging {

  val supervisor = Supervisor()

  def receive = {
    case Episode(experiences) =>
    case msg => log.info("Supervisor receive")
  }
}

