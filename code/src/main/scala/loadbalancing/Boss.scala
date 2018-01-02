package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import supervisory._

trait BossActor extends Actor with ActorLogging {

  val distanceFns  = Map(
    RelativeLoad -> RelativeLoad.distanceFn _,
    EnvironmentRate -> EnvironmentRate.distanceFn _,
    AgentRate -> AgentRate.distanceFn _
  )

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
  def addEnvironmentRates(rates: Map[ActorRef, Double]) =
    this.environmentRates = Some((rates map {
        case (actor, rate) => EnvironmentRate(actor, rate)
    }).toList)
  def addAgentRates(rates: Map[ActorRef, Double]) =
    this.agentRates = Some((rates map {
      case (actor, rate) => AgentRate(actor, rate)
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

case class AgentRateCalc() {
  private var numNeighbors: Option[Integer] = None
  private var neighborRates = Map.empty[ActorRef, Double]

  def addNumNeighbors(numNeighbors: Integer) =
    this.numNeighbors = Some(numNeighbors)
  def addNeighborRate(actor: ActorRef, rate: Double) =
    this.neighborRates += actor -> rate
  def isComplete = numNeighbors.isDefined &&
    numNeighbors.get == neighborRates.size
  def result = neighborRates
}

case class AgentRateActor(actor: ActorRef)
  extends Actor with ActorLogging {

  val calc = AgentRateCalc()
  actor ! NeighborRequest

  def receive = {
    case Neighbors(neighbors) =>
      calc.addNumNeighbors(neighbors.length)
      neighbors.map(neighbor => neighbor ! AgentRateRequest)
    case Rate(rate) =>
      calc.addNeighborRate(sender, rate)
      if (calc isComplete) {
        context.parent ! SolutionAgentRate(actor, calc result)
        context.stop(self)
      }
  }
}

case class EnvironmentRateCalc() {
  private var numNeighbors: Option[Integer] = None
  private var neighborRates = Map.empty[ActorRef, Double]

  def addNumNeighbors(numNeighbors: Integer) =
    this.numNeighbors = Some(numNeighbors)
  def addNeighborRate(actor: ActorRef, rate: Double) =
    this.neighborRates += actor -> rate
  def isComplete = numNeighbors.isDefined &&
    numNeighbors.get == neighborRates.size
  def result = neighborRates
}

case class EnvironmentRateActor(actor: ActorRef)
  extends Actor with ActorLogging {

  val calc = EnvironmentRateCalc()
  actor ! NeighborRequest

  def receive = {
    case Neighbors(neighbors) =>
      calc.addNumNeighbors(neighbors.length)
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
  private var numNeighbors: Option[Integer] = None
  private var neighborLoads: List[Double] = Nil

  def addAgentLoad(serviceTime: Double) =
    this.agentLoad = Some(serviceTime)
  def addNumNeighbors(numNeighbors: Integer) =
    this.numNeighbors = Some(numNeighbors)
  def addNeighborLoad(serviceTime: Double) =
    this.neighborLoads = serviceTime :: this.neighborLoads
  def isComplete = agentLoad.isDefined && numNeighbors.isDefined &&
    numNeighbors.get == neighborLoads.length
  def result = agentLoad.get / (neighborLoads.sum / neighborLoads.length)

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
case class FinishedCalc(actor: ActorRef, )

case class CalculateRelativeLoad(actor: ActorRef)
case class SolutionRelativeLoad(actor: ActorRef, load: Double)
case class RelativeLoad(load: Double) extends ContextFeature
object RelativeLoad extends Context {
  // x and y should be RelativeLoad (casting is bad)
  def distanceFn(x: ContextFeature, y: ContextFeature): Double = math.abs(
      x.asInstanceOf[RelativeLoad].load - y.asInstanceOf[RelativeLoad].load)
}

case class CalculateEnvironmentRate(actor: ActorRef)
case class SolutionEnvironmentRate(actor: ActorRef,
                                   rates: Map[ActorRef, Double])
case class EnvironmentRate(agent: ActorRef, rate: Double) extends ContextFeature
object EnvironmentRate extends Context {
  // x and y should be EnvironmentRate (casting is bad)
  def distanceFn(x: ContextFeature, y: ContextFeature): Double = math.abs(
    x.asInstanceOf[EnvironmentRate].rate - y.asInstanceOf[EnvironmentRate].rate)
}

case class CalculateAgentRate(actor: ActorRef)
case class SolutionAgentRate(actor: ActorRef,
                             rates: Map[ActorRef, Double])
case class AgentRate(agent: ActorRef, rate: Double) extends ContextFeature
object AgentRate extends Context {
  // x and y should be AgentRate (casting is bad)
  def distanceFn(x: ContextFeature, y: ContextFeature): Double = math.abs(
    x.asInstanceOf[AgentRate].rate - y.asInstanceOf[AgentRate].rate)
}