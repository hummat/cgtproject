package supervisory

import akka.actor.{Actor, ActorLogging}

trait Subordinate extends Actor with ActorLogging {

  def receive = {
    case msg => log.info("Subordinate receive")
  }
}
