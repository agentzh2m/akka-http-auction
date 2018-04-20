package me.hamuel.bigdata

import akka.actor.{Actor, ActorLogging, Props}

class AuctionActor(auctionId: String) extends Actor with ActorLogging{
  import AuctionActor._
  var currentHighestPrice: Long = 0
  var currentWinner: String = ""
  def receive: Receive = {
    case GetInfo =>
      sender() ! BidInfo(currentHighestPrice, currentWinner)
    case BidNewPrice(price, bidder) =>
      if (price > currentHighestPrice) {
        currentHighestPrice = price
        currentWinner = bidder
        sender() ! BidResult(currentHighestPrice, Winning)
      }else {
        sender() ! BidResult(currentHighestPrice, Losing)
      }
    case CloseBid =>
      sender() ! BidInfo(currentHighestPrice, currentWinner)
      context.stop(self)
  }

}

object AuctionActor {
  sealed case class BidInfo(currentPrice: Long, currentWinner: String)
  sealed case class BidResult(currentPrice: Long, status: ResultStatus)
  case object GetInfo
  sealed case class BidNewPrice(price: Long, bidderId: String)
  case object CloseBid
  trait ResultStatus
  case object Winning extends ResultStatus
  case object Losing extends ResultStatus

  def props(auctionId: String) = Props(new AuctionActor(auctionId))
}
