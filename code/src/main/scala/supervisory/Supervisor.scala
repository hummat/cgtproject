package supervisory

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.util.Random

/** This composes the supervising computation */
case class Supervisor() {

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

  def assessSimilarity(agent: ActorRef): List[ActorRef] = {

    // TODO: implement noise in ContextFeature signal

    // calculate pairwise distances and convert to Boltzmann exponential
    // actor -> other -> feature -> value
    val agentFeatures = contextFeatures(agent).sortBy(_.context)
    var states = Map.empty[String, Set[Double]] // distinct states of context
    var m: Map[ActorRef, List[(String, Double)]] =
      (contextFeatures - agent) filter {
        case (other: ActorRef, features: List[ContextFeature]) =>
          agentFeatures.length == features.length
      } map {
        case (other: ActorRef, features: List[ContextFeature]) => other -> (
          agentFeatures zip features.sortBy(_.context) map {
            case (a, b) =>
              val bz = exp(a.distanceFrom(b))
              states = states + (a.context -> (states.getOrElse(a.context,
                Set.empty[Double]) + bz))
              a.context -> bz
          })
      }

    // actor -> other -> feature -> probability
    val ps: Map[ActorRef, List[(String, Double)]] = m map {
      case (other, features) => other -> (features map {
        case (feature, value) =>
          feature -> value / states(feature).sum
      })
    }

    // just average the probabilities for now
    // actor -> other -> probability
    val p = for ((other, features) <- ps) yield
      other -> features.map(_._2).sum / features.size

    // pick actors to share with
    val as = p filter (Random.nextDouble <= _._2) keys

    as.toList
  }
}

/** This does the actor stuff */
trait SupervisorActor extends Actor with ActorLogging {

  lazy val supervisor = Supervisor()

  def receive = {
    case Episode(experiences) =>
      supervisor.addExperiences(sender, experiences)
      self ! Calculate(sender)
    case ContextVector(agent, vector) =>
      supervisor.addContextFeatures(agent, vector)
      val shares = supervisor.assessSimilarity(agent)
      log.info(s"${shares.length}")
      shares.foreach { case other =>
        other ! Episode(supervisor.experiences(agent))
      }
//    case msg => log.info("Supervisor receive")
  }
}

case class Calculate(actor: ActorRef)

case class Finished(actor: ActorRef, vector: List[ContextFeature])

trait ContextFeature {
  val context: String

  def distanceFrom(other: ContextFeature): Double
}

case class ContextVector(agent: ActorRef, vector: List[ContextFeature])
