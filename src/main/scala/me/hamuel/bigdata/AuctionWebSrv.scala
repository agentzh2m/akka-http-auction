package me.hamuel.bigdata

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import me.hamuel.bigdata.AuctionActor.BidInfo
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.io.StdIn

final case class Auction(auction_id: String)
final case class Bid(bid_price: Long, bidder_id: String)
final case class WonStatus(status: String = "won")
final case class LoseStatus(current_price: Long, status: String = "lose")
final case class WinnerStatus(bid_winner_id: String, current_price: Long)
final case class NoAuctionFound(message : String = "no auction found")
final case class HelloWorld(message: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val auctionFormat = jsonFormat1(Auction)
  implicit val bidFormat = jsonFormat2(Bid)
  implicit val wonStatusFormat = jsonFormat1(WonStatus)
  implicit val loseStatusFormat = jsonFormat2(LoseStatus)
  implicit val winnerStatus = jsonFormat2(WinnerStatus)
  implicit val noAucFormat = jsonFormat1(NoAuctionFound)
  implicit val helloFormat = jsonFormat1(HelloWorld)
}

object AuctionWebSrv extends Directives with JsonSupport {
  val SERVER_HOST = "localhost"
  val SERVER_PORT = 8080
  implicit val system: ActorSystem = ActorSystem("auction-web-server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val timeout = Timeout(5 seconds)
  val auctionSystem = system.actorOf(AuctionGuardianActor.props, "auction-guardian")

  def createAuction: Route =
    post {
      val f = (auctionSystem ? AuctionGuardianActor.NewAuction).mapTo[AuctionGuardianActor.AuctionInfo]
      onSuccess(f) {
        case auc : AuctionGuardianActor.AuctionInfo => complete(Auction(auc.auctionId))
        case _ => complete(HelloWorld("wtf"))
      }
    }

  def createBid(auctionId: String): Route =
    post {
      entity(as[Bid]) { bid =>
        import AuctionActor._
        val f = auctionSystem ? AuctionGuardianActor.MakeBid(auctionId, bid.bidder_id, bid.bid_price)
        onSuccess(f) {
          case bidres: BidResult => bidres.status match {
            case Winning => complete(WonStatus())
            case Losing => complete(LoseStatus(bidres.currentPrice))
          }
          case AuctionGuardianActor.AuctionDoesNotExist => complete(StatusCodes.BadRequest, NoAuctionFound())
        }
      }
    }

  def getBidStatus(auctionId: String): Route =
    get {
      import AuctionActor._
      val f = auctionSystem ? AuctionGuardianActor.AuctionInfo(auctionId)
      onSuccess(f) {
        case b: BidInfo => complete(WinnerStatus(b.currentWinner, b.currentPrice))
        case AuctionGuardianActor.AuctionDoesNotExist => complete(StatusCodes.BadRequest, NoAuctionFound())
      }
    }

  def closeBid(auctionId: String): Route =
    delete {
      val f = auctionSystem ? AuctionGuardianActor.CloseBid(auctionId)
      onSuccess(f){
        case b: BidInfo => complete(WinnerStatus(b.currentWinner, b.currentPrice))
        case AuctionGuardianActor.AuctionDoesNotExist => complete(StatusCodes.BadRequest, NoAuctionFound())
      }
    }

  def createRoute: Route = {
    path("hello") {
      auctionSystem ! AuctionGuardianActor.Hello
      complete(HelloWorld("hello people"))
    } ~
      pathPrefix("auction"){
        parameter('create){ (_) =>
          createAuction
        } ~ path(RemainingPath) { id =>
          createBid(id.toString) ~
            getBidStatus(id.toString) ~
            closeBid(id.toString)
        }
      }
  }

  def main(args: Array[String]): Unit = {
    val route = createRoute
    val bindingFuture =
      Http().bindAndHandle(route, SERVER_HOST, SERVER_PORT)
    println(s"Server running at http://$SERVER_HOST:$SERVER_PORT")
    println("Press Enter to stop")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete { _ => system.terminate() }
  }

}
