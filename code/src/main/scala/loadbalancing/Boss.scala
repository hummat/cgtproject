package loadbalancing

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSelection, Props}
import supervisory._

trait BossActor extends Actor with ActorLogging {

  def receive = {
    case Calculate(actor) =>
      context.actorOf(Props(classOf[BossCalcActor], actor))
    case Finished(actor, vector) =>
      self ! ContextVector(actor, vector)
  }
}

case class BossCalc() {
  private var relativeLoad: Option[RelativeLoad] = None
  private var environmentRates: Option[List[EnvironmentRate]] = None
  private var agentRates: Option[List[AgentRate]] = None

  def addRelativeLoad(relativeLoad: Double) =
    this.relativeLoad = Some(RelativeLoad(relativeLoad))
  def addEnvironmentRates(rates: Map[Int, Double]) =
    this.environmentRates = Some((rates map {
        case (index, rate) => EnvironmentRate(index, rate)
    }).toList)
  def addAgentRates(rates: Map[Int, Double]) =
    this.agentRates = Some((rates map {
      case (index, rate) => AgentRate(index, rate)
    }).toList)
  def isComplete = relativeLoad.isDefined && environmentRates.isDefined &&
    agentRates.isDefined
  def result = relativeLoad.get :: environmentRates.get ::: agentRates.get
}

case class BossCalcActor(actor: ActorRef) extends Actor with ActorLogging {

  val calc = BossCalc()

  self ! CalculateRelativeLoad(actor)
  self ! CalculateEnvironmentRate(actor)
  self ! CalculateAgentRate(actor)

  def receiveResult: Receive = {
    case CalculateRelativeLoad(actor) =>
      context.actorOf(Props(classOf[RelativeLoadActor], actor))
    case SolutionRelativeLoad(actor, load) =>
      calc addRelativeLoad load
    case CalculateEnvironmentRate(actor) =>
      context.actorOf(Props(classOf[EnvironmentRateActor], actor))
    case SolutionEnvironmentRate(actor, rates) =>
      calc addEnvironmentRates rates
    case CalculateAgentRate(actor) =>
      context.actorOf(Props(classOf[AgentRateActor], actor))
    case SolutionAgentRate(actor, rates) =>
      calc addAgentRates rates
  }

  def checkComplete: Receive = {
    case msg =>
      if (calc isComplete) {
        context.parent ! Finished(actor, calc.result)
        context.stop(self)
      }
  }

  def receive = receiveResult andThen checkComplete
}

case class AgentRateCalc(context: ActorContext) {
  private var neighbors: Option[List[ActorSelection]] = None
  private var neighborRates = Map.empty[ActorRef, Double]

  def addNeighbors(neighbors: List[ActorSelection]) =
    this.neighbors = Some(neighbors)
  def addNeighborRate(actor: ActorRef, rate: Double) =
    this.neighborRates += actor -> rate
  def isComplete = neighbors.isDefined &&
    neighbors.get.length == neighborRates.size
  def result = neighborRates map {
    case (actor, rate) =>
      val n = neighbors.get
      val sel = context.system.actorSelection(actor.path)
      n.indexOf(n.find(_ == sel).get) -> rate
  }
}

case class AgentRateActor(actor: ActorRef)
  extends Actor with ActorLogging {

  val calc = AgentRateCalc(context)
  actor ! NeighborRequest

  def receive = {
    case Neighbors(neighbors) =>
      calc.addNeighbors(neighbors)
      neighbors.map(neighbor => neighbor ! AgentRateRequest)
    case Rate(rate) =>
      calc.addNeighborRate(sender, rate)
      if (calc isComplete) {
        context.parent ! SolutionAgentRate(actor, calc result)
        context.stop(self)
      }
  }
}

case class EnvironmentRateCalc(context: ActorContext) {
  private var neighbors: Option[List[ActorSelection]] = None
  private var neighborRates = Map.empty[ActorRef, Double]

  def addNeighbors(neighbors: List[ActorSelection]) =
    this.neighbors = Some(neighbors)
  def addNeighborRate(actor: ActorRef, rate: Double) =
    this.neighborRates += actor -> rate
  def isComplete = neighbors.isDefined &&
    neighbors.get.length == neighborRates.size
  def result = neighborRates map {
    case (actor, rate) =>
      val n = neighbors.get
      val sel = context.system.actorSelection(actor.path)
      n.indexOf(n.find(_ == sel).get) -> rate
  }
}

case class EnvironmentRateActor(actor: ActorRef)
  extends Actor with ActorLogging {

  val calc = EnvironmentRateCalc(context)
  actor ! NeighborRequest

  def receive = {
    case Neighbors(neighbors) =>
      calc.addNeighbors(neighbors)
      neighbors.map(neighbor => neighbor ! EnvironmentRateRequest)
    case Rate(rate) =>
      calc.addNeighborRate(sender, rate)
      if (calc isComplete) {
        context.parent ! SolutionEnvironmentRate(actor, calc result)
        context.stop(self)
      }
  }
}

case class RelativeLoadCalc() {
  private var agentLoad: Option[Double] = None
  private var numNeighbors: Option[Int] = None
  private var neighborLoads: List[Double] = Nil

  def addAgentLoad(serviceTime: Double) =
    this.agentLoad = Some(serviceTime)
  def addNumNeighbors(numNeighbors: Int) =
    this.numNeighbors = Some(numNeighbors)
  def addNeighborLoad(serviceTime: Double) =
    this.neighborLoads = serviceTime :: this.neighborLoads
  def isComplete = agentLoad.isDefined && numNeighbors.isDefined &&
    numNeighbors.get == neighborLoads.length
  def result = {
    val avg = math.max(1, (neighborLoads.sum / neighborLoads.length))
    agentLoad.get / avg
  }

}

case class RelativeLoadActor(actor: ActorRef)
  extends Actor with ActorLogging {

  val calc = RelativeLoadCalc()
  actor ! NeighborRequest
  actor ! LoadRequest

  def receive = {
    case Neighbors(neighbors) =>
      calc.addNumNeighbors(neighbors.length)
      neighbors.map(neighbor => neighbor ! LoadRequest)
    case Load(serviceTime) =>
      if (sender.path == actor.path) calc.addAgentLoad(serviceTime)
      else calc.addNeighborLoad(serviceTime)
      if (calc isComplete) {
        context.parent ! SolutionRelativeLoad(actor, calc result)
        context.stop(self)
      }
  }
}

case class CalculateRelativeLoad(actor: ActorRef)
case class SolutionRelativeLoad(actor: ActorRef, load: Double)
case class RelativeLoad(load: Double) extends ContextFeature {
  val context = RelativeLoad.toString
  def distanceFrom(other: ContextFeature) = {
    import math._
    Quantize(pow(E, -abs(this.load - other.asInstanceOf[RelativeLoad].load)))
  }
}

case class CalculateEnvironmentRate(actor: ActorRef)
case class SolutionEnvironmentRate(actor: ActorRef, rates: Map[Int, Double])
case class EnvironmentRate(index: Int, rate: Double) extends ContextFeature {
  val context = EnvironmentRate.toString + index.toString
  def distanceFrom(other: ContextFeature) = {
    import math._
    Quantize(pow(E, -abs(this.rate - other.asInstanceOf[EnvironmentRate].rate)))
  }
}

case class CalculateAgentRate(actor: ActorRef)
case class SolutionAgentRate(actor: ActorRef, rates: Map[Int, Double])
case class AgentRate(index: Int, rate: Double) extends ContextFeature {
  val context = AgentRate.toString + index.toString
  def distanceFrom(other: ContextFeature) = {
    import math._
    Quantize(pow(E, -abs(this.rate - other.asInstanceOf[AgentRate].rate)))
  }
}

object Quantize {
  import math._
  def apply(value: Double): Double = {
    quantize(value, 0.0, 0.1)
  }
  def quantize(value: Double, level: Double, inc: Double): Double = {
    if(max(level, value) != level) level
    else quantize(value, level + inc, inc)
  }
}
