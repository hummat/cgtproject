package loadbalancing

import akka.actor.{Actor, ActorLogging}

trait Worker extends Actor with ActorLogging {

  def receive = {
    case msg => log.info("Worker receive")
  }

}
