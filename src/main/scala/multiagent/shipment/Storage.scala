package multiagent.shipment

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill}

import scala.collection.mutable
import scala.collection.mutable.HashSet

class Storage(agentsCount : Int, Pos : Int) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    log.info("Storage started in " + Pos.toString)
  }
  override def postStop(): Unit = log.info("Storage stopped")

  val mailList = new HashSet[ActorRef]()
  var finished = new mutable.HashSet[NotifyFinish]()
  var delta = 0
  def handleNewUser(newUser: ActorRef) = {
    mailList.foreach(actor => {
      actor ! NewUser(newUser)
      newUser ! NewUser(actor)
    })
    mailList.add(newUser)
  }

  def printStats(): Unit = {
    val sep = "\n------------------------------------------------------\n"
    val stats =
      finished.toList.sortBy{x => x.agentId}.map( x => {
        val msg = s"Agent ${x.agentId} (${x.A}-${x.B}) final message is: ${x.finalMsg}\nRoutes:\n${x.routes}"
        msg
      }).mkString(sep, sep, sep)
    log.info(s"\n STATISTICS ${sep}Storage($Pos): summary delta is $delta" + stats + "Press ENTER")
  }

  override def receive: Receive = {
    case GetStoragePosition(id) => {
      val s = sender()
      s ! RespondStoragePosition(id, Pos)
      handleNewUser(s)
    }
    case NotifyFinish(id, userDelta, userA, userB, finalMsg, routes) => {
      finished.add(NotifyFinish(id, userDelta, userA, userB, finalMsg, routes))
      delta += userDelta
      //sender() ! PoisonPill
      if (finished.size == agentsCount) {
        printStats()
        context.parent ! PoisonPill
      }
    }
  }
}
