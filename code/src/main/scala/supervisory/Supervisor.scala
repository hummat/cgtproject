package supervisory

import akka.actor.{Actor, ActorLogging, ActorRef}
import supervisory.Supervisor.DistanceFns

import scala.util.Random

object Supervisor {
  type DistanceFns =
    Map[Context, (ContextFeature, ContextFeature) => Double]
}

/** This composes the supervising computation */
case class Supervisor(distanceFns: DistanceFns) {

  val tau = 1

  var experiences = Map.empty[ActorRef, List[Experience]]
  var contextFeatures = Map.empty[ActorRef, List[ContextFeature]]

  def addExperiences(actor: ActorRef,
                     actorExperiences: List[Experience]) =
    experiences = experiences + (actor -> actorExperiences)

  def addContextFeatures(actor: ActorRef,
                         actorContextFeatures: List[ContextFeature]) =
    contextFeatures = contextFeatures + (actor -> actorContextFeatures)

  def exp(value: Double): Double = math.pow(math.E, value / tau)

//  def assessSimilarity(agent: ActorRef): List[ActorRef] = {
//
//    //only need to do this for the input agent, not all agents
//
//    // calculate pairwise distances and convert to Boltzmann exponential
//    // actor -> other -> feature -> value
//    val m =
//    contextFeatures map {
//      case (actor, features) => actor -> (
//        (contextFeatures - actor) map {
//          case (other, otherFeatures) => other -> (
//            if (features.length == otherFeatures.length) {
//              features zip otherFeatures map {
//                case (a, b) => a.context -> exp(a.distanceFrom(b))
//              }
//            } else Map.empty[String, Double]
//            ).toMap
//        }
//        )
//    }
//
//    // calculate Boltzmann sums
//    // actor -> feature -> value
//    var sums = Map.empty[(ActorRef, String), Double]
//    for ((actor, others) <- m) {
//      for ((other, features) <- others) {
//        for ((feature, value) <- features) {
//          sums = sums + ((actor, feature) ->
//            (value + sums.getOrElse((actor, feature), 0)))
//        }
//      }
//    }
//
//    // actor -> other -> feature -> probability
//    val ps = m map {
//      case (actor, others) => actor -> (others map {
//        case (other, features) => other -> (features map {
//          case (feature, value) =>
//            val prob = value / sums.getOrElse((actor, feature), 0)
//            feature -> prob
//        })
//      })
//    }
//
//    // just average the probabilities for now
//    // actor -> other -> probability
//    val p = for ((actor, others) <- ps) yield { actor -> (
//      for ((other, features) <- others) yield {
//        other -> features.values.sum / features.size
//      })
//    }
//
//    // pick actors to share with
//    val as = for ((actor, others) <- p) yield { actor -> (
//      others filter { case (other, prob) => Random.nextDouble <= prob }
//      map { case (other, prob) => other }
//      toList
//    )}
//
//    as(agent)
//  }
}

/** This does the actor stuff */
trait SupervisorActor extends Actor with ActorLogging {

  val distanceFns: DistanceFns

  lazy val supervisor = Supervisor(distanceFns)

  def receive = {
    case Episode(experiences) =>
      supervisor.addExperiences(sender, experiences)
      self ! Calculate(sender)
    case ContextVector(agent, vector) =>
      supervisor.addContextFeatures(agent, vector)
//      supervisor.assessSimilarity(agent).map {
//        _ ! Episode(supervisor.experiences(agent))
//      }
    case msg => log.info("Supervisor receive")
  }
}

case class Calculate(actor: ActorRef)

case class Finished(actor: ActorRef, vector: List[ContextFeature])

trait ContextFeature {
  val context: String = this.getClass.toString

  def distanceFrom(other: ContextFeature): Double
}

trait ContextFeature2 {
  type Context
  def distanceFrom(other: Context): Double
}

trait Context {
  def distanceFn(x: ContextFeature, y: ContextFeature): Double
}

case class ContextVector(agent: ActorRef, vector: List[ContextFeature])
