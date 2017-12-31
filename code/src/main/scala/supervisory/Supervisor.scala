package supervisory

import akka.actor.{Actor, ActorLogging, ActorRef}
import supervisory.Supervisor.DistanceFns

object Supervisor {
  type DistanceFns =
    Map[Context, (ContextFeature, ContextFeature) => Double]
}

/** This composes the supervising computation */
case class Supervisor(distanceFns: DistanceFns){

  var experiences = Map.empty[ActorRef, List[Experience]]
  var contextFeatures = Map.empty[ActorRef, List[ContextFeature]]

  def addExperiences(actor: ActorRef,
                     actorExperiences: List[Experience]) =
    experiences = experiences + (actor -> actorExperiences)

  def addContextFeatures(actor: ActorRef,
                         actorContextFeatures: List[ContextFeature]) =
    contextFeatures = contextFeatures + (actor -> actorContextFeatures)

  def assessSimilarity = ???

}

/** This does the actor stuff */
trait SupervisorActor extends Actor with ActorLogging {

  val distanceFns: DistanceFns

  lazy val supervisor = Supervisor(distanceFns)

  def receive = {
    case Episode(experiences) =>
      supervisor.addExperiences(sender, experiences)
    case ContextVector(agent, vector) =>
      supervisor.addContextFeatures(agent, vector)
    case msg => log.info("Supervisor receive")
  }
}

case class Calculate(actor: ActorRef, experiences: List[Experience])

trait ContextFeature
trait Context {
  def distanceFn(x: ContextFeature, y: ContextFeature): Double
}

case class ContextVector(agent: ActorRef, vector: List[ContextFeature])
