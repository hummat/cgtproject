package loadbalancing

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import supervisory._

case class Boss() {

}

trait BossActor extends Actor with ActorLogging {

//  val subordinateNeighborhoods: Map[ActorRef, List[ActorRef]]

  val distanceFns  = Map(
    RelativeLoad -> RelativeLoad.distanceFn _,
    EnvironmentRate -> EnvironmentRate.distanceFn _,
    AgentRate -> AgentRate.distanceFn _
  )

  def receive = {
    case Calculate(actor, experiences) =>
      self ! CalculateRelativeLoad(actor) // cheaty method
//      self ! CalculateEnvironmentRate(actor)
//      self ! CalculateAgentRate(actor)
    case CalculateRelativeLoad(actor) =>
      context.actorOf(Props(classOf[RelativeLoadActor], actor))
    case SolutionRelativeLoad(actor, load) =>
      context.stop(sender)

    case msg =>
  }
}

case class RelativeLoadCalc() {
  private var agentLoad: Option[Double] = None
  private var numNeighbors: Option[Integer] = None
  private var neighborLoads: List[Double] = Nil

  def addAgentLoad(serviceTime: Double) =
    this.agentLoad = Option(serviceTime)
  def addNumNeighbors(numNeighbors: Integer) =
    this.numNeighbors = Option(numNeighbors)
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
    case Load(serviceTime) =>
      if (sender.path == actor.path) calc.addAgentLoad(serviceTime)
      else calc.addNeighborLoad(serviceTime)
      if (calc isComplete)
        context.parent ! SolutionRelativeLoad(actor, calc result)
    case Neighbors(neighbors) =>
      calc.addNumNeighbors(neighbors.length)
      neighbors.map(neighbor => neighbor ! LoadRequest)
  }
}

case class CalculateRelativeLoad(actor: ActorRef)
case class SolutionRelativeLoad(actor: ActorRef, load: Double)

case class RelativeLoad(load: Double) extends ContextFeature
object RelativeLoad extends Context {
  // x and y should be RelativeLoad (casting is bad)
  def distanceFn(x: ContextFeature, y: ContextFeature): Double = math.abs(
      x.asInstanceOf[RelativeLoad].load - y.asInstanceOf[RelativeLoad].load)
}

case class EnvironmentRate(agent: ActorRef, rate: Double) extends ContextFeature
object EnvironmentRate extends Context {
  // x and y should be EnvironmentRate (casting is bad)
  def distanceFn(x: ContextFeature, y: ContextFeature): Double = math.abs(
    x.asInstanceOf[EnvironmentRate].rate - y.asInstanceOf[EnvironmentRate].rate)
}

case class AgentRate(agent: ActorRef, rate: Double) extends ContextFeature
object AgentRate extends Context {
  // x and y should be AgentRate (casting is bad)
  def distanceFn(x: ContextFeature, y: ContextFeature): Double = math.abs(
    x.asInstanceOf[AgentRate].rate - y.asInstanceOf[AgentRate].rate)
}