package me.hamuel.bigdata

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.apache.commons.lang3.RandomStringUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object AuctionGuardianActor {
  case object NewAuction
  final case class AuctionInfo(auctionId: String)
  final case class MakeBid(auctionId: String, bidderId: String, price: Long)
  final case class CloseBid(auctionId: String)
  case object AuctionAlreadyExist
  case object AuctionDoesNotExist
  case object Hello
  def props = Props(new AuctionGuardianActor)
}

class AuctionGuardianActor extends Actor with ActorLogging{
  import AuctionGuardianActor._

  import scala.collection.mutable
  implicit val timeout = Timeout(5 seconds)
  val auctions: mutable.Map[String, ActorRef] = mutable.Map()
  val system = ActorSystem("auction-guardian")
  def receive: Receive = {
    case Hello => log.info("yeh receive something")
    case NewAuction =>
      //there is an chance where the auctionId will collide with each other
      val auctionId = RandomStringUtils.randomAlphanumeric(6)
      auctions += ((auctionId, system.actorOf(AuctionActor.props(auctionId))))
      sender() ! AuctionInfo(auctionId)
    case MakeBid(auctionId, bidderId, price) =>
      val auction = auctions.get(auctionId)
      auction match {
        case Some(actorRef) =>
          (actorRef ? AuctionActor.BidNewPrice(price, bidderId)) pipeTo sender()
        case None =>
          sender() ! AuctionDoesNotExist
      }
    case AuctionInfo(auctionId) =>
      val auction = auctions.get(auctionId)
      auction match {
        case Some(actorRef) =>
          (actorRef ? AuctionActor.GetInfo) pipeTo sender()
        case None => sender() ! AuctionDoesNotExist
      }
    case CloseBid(auctionId) =>
      val auction = auctions.get(auctionId)
      auction match {
        case Some(actorRef) =>
          (actorRef ? AuctionActor.CloseBid) pipeTo sender()
          auctions -= auctionId
        case None => sender() ! AuctionDoesNotExist
      }
  }
}


