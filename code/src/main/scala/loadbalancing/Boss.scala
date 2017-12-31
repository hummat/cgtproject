package loadbalancing

import akka.actor.{Actor, ActorLogging}

case class Boss()

trait BossActor extends Actor with ActorLogging {
  def receive = {
    case msg =>
  }
}
