package supervisory

import akka.actor.{Actor, ActorLogging}

trait Supervisor extends Actor with ActorLogging {

  def receive = {
    case msg => log.info("Supervisor receive")
  }
}

