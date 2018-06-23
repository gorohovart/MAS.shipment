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
import scala.collection.JavaConverters._

class SimpleActor(myID : Int, pathHandler : PathHandler, storageAgent : ActorRef) extends Actor with ActorLogging with Timers {
  private case object DeliveryOptionsTick
  private case object DeliveryRequestTick
  private case object RequestsWaitTick
  val maxRequests = 5

  var myA = 0
  var myB = 0
  var delta = 0
  var firstDeliveryPoint = true
  var acceptRequests = true
  var retryCounter = 0
  var waitingOfferAnswer : Option[DeliveryOption] = None
  var requestIteration = -1
  var reqCounter = 0
  val mailList = new HashSet[ActorRef]()
  val myRoutes = new ListBuffer[ListBuffer[(Int, Int)]]()
  val acceptedOffers = new mutable.HashMap[Int, (() => Unit)]()
  val destinations = new HashSet[Int]()
  var needToFindSomeone = false
  var myPriceForDelivery = 0
  var storagePoint = -1
  var isDeliveryOptionsWaitTimerSet = false
  var isRequestsWaitTimerSet = false
  val deliveryOptions = new HashSet[DeliveryOption]()
  val offersFromMe = new mutable.HashMap[Int, Int]()
  var deliveryChoice : DeliveryChoice = null
  var myFinalMsg = ""
  override def preStart(): Unit = {
    myA = pathHandler.getRandomPosition()
    myB = pathHandler.getRandomPosition()
    storageAgent ! GetStoragePosition(0)
    timers.startSingleTimer(TickGetStoragePosition, TickGetStoragePosition, 1 second)
    log.info("Started with positions " + myA.toString + " and " + myB.toString)
  }

  override def postStop(): Unit = {
    log.info("dead x_x")
  }

  def handleStoragePositionInbox(X : Int) = {
    storagePoint = X
    val AtoB = pathHandler.getPathLength(myA, myB)
    val AtoX = pathHandler.getPathLength(myA, storagePoint)
    val XtoB = pathHandler.getPathLength(storagePoint, myB)
    myPriceForDelivery = AtoX + XtoB - AtoB
    needToFindSomeone = myPriceForDelivery > 0
    if (!needToFindSomeone) {
      myFinalMsg += "Will drive myself, storage is on my way. "
      log.info(myFinalMsg)
      setRouteThroughStorage()
    }
    else{
      timers.startSingleTimer(DeliveryRequestTick, DeliveryRequestTick, 2 second)
    }
    timers.startSingleTimer(RequestsWaitTick, RequestsWaitTick, 6 second)
  }

  def setRouteThroughStorage() = {
    val l = new ListBuffer[(Int, Int)]()
    l.append((myA,storagePoint))
    l.append((storagePoint, myB))
    myRoutes.append(l)
  }
  def setDefaultRoute() = {
    val l = new ListBuffer[(Int, Int)]()
    l.append((myA, myB))
    myRoutes.append(l)
  }

  def getPriceForPoint(deliveryPoint : Int): Int = {
    if (needToFindSomeone) {
      val p1 =
        (pathHandler.getPathLength(myA, storagePoint)
      + pathHandler.getPathLength(storagePoint, deliveryPoint)
      + pathHandler.getPathLength(deliveryPoint, myB)
      - pathHandler.getPathLength(myA, myB))
      val p2 =
        (pathHandler.getPathLength(myB, storagePoint)
      + pathHandler.getPathLength(storagePoint, deliveryPoint)
      + pathHandler.getPathLength(deliveryPoint, myA)
      - pathHandler.getPathLength(myA, myB))
      val p3 =
        (pathHandler.getPathLength(myA, storagePoint)
      + pathHandler.getPathLength(storagePoint, myB)
      + pathHandler.getPathLength(myB, deliveryPoint)
      + pathHandler.getPathLength(deliveryPoint, myA)
      - 2 * pathHandler.getPathLength(myA, myB))
      min(p1, min(p2, p3))
    }
    else {
      val priceIfAddToExistingRoutes : Int =
        myRoutes.map( route => {
          route.map{ case (from, to) => {
            if (to == storagePoint)
              Int.MaxValue
            else
              (pathHandler.getPathLength(from, deliveryPoint)
                + pathHandler.getPathLength(deliveryPoint, to)
                - pathHandler.getPathLength(from, to))
          }}.min
        }).min
      val priceIfAddNewRoute =
        (pathHandler.getPathLength(myA, deliveryPoint)
        + pathHandler.getPathLength(deliveryPoint, myB)
        - pathHandler.getPathLength(myA, myB))

      if (priceIfAddNewRoute < 0) log.error("priceIfAddNewRoute is < 0")
      if (priceIfAddToExistingRoutes < 0) log.error("priceIfAddToExistingRoutes is < 0")
      min(priceIfAddToExistingRoutes, priceIfAddNewRoute)
    }
  }

  def addDestination(deliveryPoint : Int, price : Int): Unit = {
    if (needToFindSomeone) {
      setRouteThroughStorage()
      myFinalMsg += "accepted delivery request, thus will drive myself"
      log.info(myFinalMsg)
      needToFindSomeone = false
    }
    if(firstDeliveryPoint){
      firstDeliveryPoint = false
      myFinalMsg += s"drive to destinations:$deliveryPoint for $price"
    }
    else{
      myFinalMsg += s", $deliveryPoint for $price"
    }
    log.info(s"added destination point $deliveryPoint")
    val minPrice =
      myRoutes.map( route => {
        route.zipWithIndex.map{ case ((from, to), i) => {
          if (to == storagePoint){
            (Int.MaxValue, i)
          }
          else {
            (pathHandler.getPathLength(from, deliveryPoint)
              + pathHandler.getPathLength(deliveryPoint, to)
              - pathHandler.getPathLength(from, to), i)
          }
        }}.minBy{ case (v, i) => v}
      }).zipWithIndex.minBy{case ((v,j), i) => v}

    val priceIfAddToExistingRoutes = minPrice._1._1
    val priceIfAddNewRoute =
      pathHandler.getPathLength(myA, deliveryPoint)
    + pathHandler.getPathLength(deliveryPoint, myB)
    - pathHandler.getPathLength(myA, myB)

    if (priceIfAddToExistingRoutes > priceIfAddNewRoute) {
      val l = new ListBuffer[(Int, Int)]()
      l.append((myA,deliveryPoint))
      l.append((deliveryPoint, myB))
      myRoutes.append(l)
    }
    else {
      val i = minPrice._2
      val j = minPrice._1._2
      val routeSection = myRoutes(i)(j)
      val newSection = List((routeSection._1, deliveryPoint), (deliveryPoint, routeSection._2))
      myRoutes(i).remove(j)
      myRoutes(i).insertAll(j, newSection)
    }
    destinations.add(deliveryPoint)
  }
  //as offerer
  def handleDeliveryRequest(sender: ActorRef, senderId : Int, pointA : Int, pointB : Int) = {
    reqCounter += 1
    if (acceptRequests) {
      val price = Math.min(getPriceForPoint(myA), getPriceForPoint(myB))
      val y = offersFromMe.get(senderId)
      if (y.isDefined){
        // already offered
        if (y.get > price){
          // we can offer better price then earlier
          log.info(s"Got request from $senderId, making new offer")
          sender ! DeliveryOffer(myID, price)
        }
        else{
          log.info(s"Got request from $senderId, can't make better offer")
        }
      }
      else{
        log.info(s"Got request from $senderId, making first offer")
        sender ! DeliveryOffer(myID, price)
      }
    }
  }
  //as requester
  def handleDeliveryOffer(user: ActorRef, senderId: Int, price: Int): Unit = {
    if (!isDeliveryOptionsWaitTimerSet) {
      timers.startSingleTimer(DeliveryOptionsTick, DeliveryOptionsTick, 1 second)
    }
    deliveryOptions.add(DeliveryOption(user, senderId, price))
    log.info(s"Got offer from $senderId")
  }
  //as requester
  def makeDeliveryChoice(): Unit = {
    if (needToFindSomeone) {
      val minOptionPriceOffer = deliveryOptions.minBy{ case DeliveryOption(user, id, price) => price}
      if (minOptionPriceOffer.price < myPriceForDelivery) {

        //log.info(s"User ${minOptionPrice.senderId} will carry my package")
        deliveryOptions.remove(minOptionPriceOffer)
        waitingOfferAnswer = Some(minOptionPriceOffer)
        minOptionPriceOffer.unit ! AcceptDeliveryOffer(myID, myA, myB)
        timers.startSingleTimer(TickAcceptDeliveryOffer, TickAcceptDeliveryOffer, 1 second)
      }
      else {
        log.info(s"Processed ${deliveryOptions.size} offers, but nothing good (min is ${minOptionPriceOffer.price}, when my is $myPriceForDelivery")
        deliveryOptions.clear()
      }

    }
  }
  // as offerer
  def handleDeliveryOfferAcceptance(unit: ActorRef, senderId : Int, pointA: Int, pointB: Int): Unit = {
    val alreadyAccepted = acceptedOffers.get(senderId)
    if (alreadyAccepted.isDefined) {
      val answer = alreadyAccepted.get
      answer()
    }
    else {
      if (acceptRequests) {
        val minPointPrice = {
          val Ap = getPriceForPoint(pointA)
          val Bp = getPriceForPoint(pointB)
          if (Ap > Bp) (pointB, Bp)
          else (pointA, Ap)
        }
        addDestination(minPointPrice._1, minPointPrice._2)
        delta += minPointPrice._2
        def answer() {
          unit ! NotifyFinalPriceAndPoint(myID, minPointPrice._2, minPointPrice._1)
        }
        answer()
        acceptedOffers.put(senderId, answer)
      }
      else {
        def answer() {
          unit ! NotifyOfferCancellation(myID)
        }
        answer()
        acceptedOffers.put(senderId, answer)
      }
    }
  }
  // as requester
  def handlePriceAndPointNotification(unit: Int, price: Int, point: Int): Unit = {
    needToFindSomeone = false
    acceptRequests = false
    waitingOfferAnswer = None
    setDefaultRoute()
    deliveryChoice = DeliveryChoice(unit, point, price)
    myFinalMsg += s"User $unit will carry my package to $point for price $price"
    log.info(s"$myFinalMsg. (A = $myA, B = $myB)")
  }
  // as requester
  def makeDeliveryRequests(): Unit = {
    if (needToFindSomeone) {
      if (requestIteration == maxRequests) {
        setRouteThroughStorage()
        myFinalMsg += s"Done $maxRequests requests and haven't got good offers, thus will drive myself. "
        log.info(myFinalMsg)
        needToFindSomeone = false
      }
      else {
        log.info(s"Sent requests to ${mailList.size} users")
        requestIteration += 1
        mailList.foreach(unit => unit ! RequestDelivery(myID, myA, myB))
        timers.startSingleTimer(DeliveryRequestTick, DeliveryRequestTick, 2 second)
      }
    }
  }

  // as offerer
  def checkNewRequests(): Unit = {
    if (reqCounter > 0 || waitingOfferAnswer.isDefined){
      reqCounter = 0
      timers.startSingleTimer(RequestsWaitTick, RequestsWaitTick, 6 second)
    }
    else {
      reportFinish()
    }
  }
  // as requester
  def handleOfferCancellation() = {
    waitingOfferAnswer = None
    makeDeliveryChoice()
  }

  def reportFinish(): Unit = {
    val routes =
      myRoutes.zipWithIndex.map{ case (route, i) => {
        val r = route.map { case (from: Int, to: Int) => {
          pathHandler.getPath(from, to).asScala.mkString("(","-", ")")
          }}.mkString("")
        s"Day ${i+1}: $r"
      }}.mkString("\n")
    //log.info(s"\nAgent $myID ($myA-$myB) final message is: $myFinalMsg\nRoutes are\n$routes")
    storageAgent ! NotifyFinish(myID, delta, myA, myB, myFinalMsg, routes)
    //timers.startSingleTimer(TickNotifyFinish, TickNotifyFinish, 1 second)
  }

  def retryDeliveryOffer(): Unit = {
    if (waitingOfferAnswer.isDefined) {
      if (retryCounter > 2) {
        waitingOfferAnswer = None
        retryCounter = 0
        makeDeliveryChoice()
      }
      else {
        retryCounter += 1
        waitingOfferAnswer.get.unit ! AcceptDeliveryOffer(myID, myA, myB)
        timers.startSingleTimer(TickAcceptDeliveryOffer, TickAcceptDeliveryOffer, 1 second)
      }
    }
  }

  def retryGetStoragePosition(): Unit = {
    if (storagePoint == -1) {
      storageAgent ! GetStoragePosition(0)
      timers.startSingleTimer(TickGetStoragePosition, TickGetStoragePosition, 1 second)
    }
  }

  override def receive: Receive = {
    case RespondStoragePosition(id, pos) => handleStoragePositionInbox(pos)
    case NewUser(newUser) => mailList.add(newUser)
    case RequestDelivery(senderId, pointA, pointB) => handleDeliveryRequest(sender(), senderId, pointA, pointB)
    case DeliveryOffer(senderId, price) => handleDeliveryOffer(sender(), senderId, price)
    case AcceptDeliveryOffer(id, pointA, pointB) => handleDeliveryOfferAcceptance(sender(), id, pointA, pointB)
    case NotifyFinalPriceAndPoint(id, price, point) => handlePriceAndPointNotification(id, price, point)
    case NotifyOfferCancellation(id) => handleOfferCancellation()
    case DeliveryRequestTick => makeDeliveryRequests()
    case RequestsWaitTick => checkNewRequests()
    case DeliveryOptionsTick => {
      isDeliveryOptionsWaitTimerSet = false
      makeDeliveryChoice()
    }
    case TickAcceptDeliveryOffer => retryDeliveryOffer()
    case TickGetStoragePosition => retryGetStoragePosition()
  }

}
