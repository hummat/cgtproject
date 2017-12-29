package supervisory

import akka.actor.{Actor, ActorLogging, ActorRef}

/** This composes the supervising computation */
case class Supervisor() {

  var neighborhood = Map.empty[ActorRef, List[Experience]]

  def add(actor: ActorRef, experiences: List[Experience]) =
    neighborhood += (actor -> experiences)

}

/** This does the actor stuff */
trait SupervisorActor extends Actor with ActorLogging {

  val supervisor = Supervisor()

  def receive = {
    case Episode(experiences) => supervisor.add(sender, experiences)
    case msg => log.info("Supervisor receive")
  }
}

