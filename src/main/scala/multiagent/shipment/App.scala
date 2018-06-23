package multiagent.shipment

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Timers}
import org.jgrapht.alg.shortestpath.JohnsonShortestPaths
import org.jgrapht.graph.SimpleGraph

import scala.collection.mutable
import scala.collection.mutable._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.io.Source
import scala.math._


final case class GetStoragePosition(requestId: Long)
final case object TickGetStoragePosition
final case object TickAcceptDeliveryOffer
//final case object TickNotifyFinish
final case class RespondStoragePosition(requestId: Long, value: Int)

final case class NotifyFinish(agentId : Int, delta : Int, A: Int, B:Int, finalMsg : String, routes: String)

final case class NewUser(actorRef: ActorRef)

final case class RequestDelivery(senderId : Int, A : Int, B : Int)
final case class DeliveryOffer(senderId : Int, price : Int)
final case class AcceptDeliveryOffer(senderId : Int, pointA : Int, pointB : Int)
final case class NotifyFinalPriceAndPoint(senderId : Int, price : Int, point : Int)
final case class NotifyOfferCancellation(senderId : Int)
case class DeliveryOption(unit : ActorRef, senderId : Int, price : Int)
case class DeliveryChoice(unit : Int, point : Int, price : Int)



object App {
  def main(args: Array[String]): Unit = {
    val graphFilePath = "./graphs/graph1.txt"
    //val graphFilePath = "./graphs/graph2.txt"
    val pathHandler = new PathHandler(graphFilePath)
    pathHandler.printGraph("graph2.dot")

    /*
    println (pathHandler.getPathLength(18, 25))
    println (pathHandler.getPathLength(18, 8))
    println (pathHandler.getPathLength(8, 25))
    println( pathHandler.getPathLength(18, 8) + pathHandler.getPathLength(8, 25) - pathHandler.getPathLength(18, 25))
    */
    ///*
    val system = ActorSystem("deliverySystem")
    try {
      val agentsCount = 10
      val storage = system.actorOf(Props(new Storage(agentsCount, pathHandler.getRandomPosition())), "storage")
      for (i <- 1 to agentsCount) {
        system.actorOf(Props(new SimpleActor(i, new PathHandler(graphFilePath), storage)), "agent" + i.toString)
      }

      // Exit the system after ENTER is pressed
      StdIn.readLine()
    } finally {
      system.terminate()
    }
    //*/
  }

}
