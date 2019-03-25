package com.example

import akka.Done

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.cluster.ddata.LWWMap
import akka.cluster.ddata.LWWMapKey
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future

/**
  * Adapted from https://github.com/akka/akka-samples/tree/2.5/akka-sample-distributed-data-scala#highly-available-shopping-cart
  *
  * The major change is that it's ask pattern based rather than using an actor, because Akka HTTP works better with
  * futures than actors.
  */
object ShoppingCart {

  import akka.cluster.ddata.Replicator._

  final case class Cart(items: Set[LineItem])

  final case class LineItem(productId: String, quantity: Int)

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

}

class ShoppingCart(system: ActorSystem) {

  import ShoppingCart._
  import akka.cluster.ddata.Replicator._

  import system.dispatcher

  private val replicator = DistributedData(system).replicator
  private implicit val cluster = Cluster(system)
  private implicit val askTimeout = Timeout(5.seconds)

  private def dataKey(userId: String) = LWWMapKey[String, LineItem]("cart-" + userId)

  def getCart(userId: String): Future[Cart] = {
    val key = dataKey(userId)

    def attemptGet(rc: ReadConsistency): Future[Cart] = {
      (replicator ? Get(key, rc)) flatMap {
        case g@GetSuccess(_, _) =>
          val data = g.get(key)
          Future.successful(Cart(data.entries.values.toSet))
        case NotFound(_, _) =>
          Future.successful(Cart(Set.empty))
        case GetFailure(_, _) =>
          attemptGet(ReadLocal)
      }
    }

    attemptGet(readMajority)
  }

  def addItem(userId: String, item: LineItem): Future[Done] = {
    (replicator ? Update(dataKey(userId), LWWMap.empty[String, LineItem], writeMajority) {
      cart => updateCart(cart, item)
    }) map handleUpdateResult
  }

  private def updateCart(data: LWWMap[String, LineItem], item: LineItem): LWWMap[String, LineItem] =
    data.get(item.productId) match {
      case Some(LineItem(_, existingQuantity)) =>
        data + (item.productId -> item.copy(quantity = existingQuantity + item.quantity))
      case None => data + (item.productId -> item)
    }

  def removeItem(userId: String, productId: String): Future[Done] = {
    val key = dataKey(userId)
    // Try to fetch latest from a majority of nodes first, since ORMap
    // remove must have seen the item to be able to remove it.
    (replicator ? Get(key, readMajority)) flatMap {
      case GetSuccess(_, _) | GetFailure(_, _) =>
        // ReadMajority failed, fallback to best effort local value
        replicator ? Update(key, LWWMap.empty[String, LineItem], writeMajority) {
          _ - productId
        }
      case NotFound(_, _) =>
        // Nothing to remove
        Future.successful(UpdateSuccess(key, None))
    } map handleUpdateResult
  }

  private def handleUpdateResult(msg: Any) = msg match {
    case UpdateSuccess(_, _) => Done
    case UpdateTimeout(_, _) =>
      // A timeout, just assume it will be eventually replicated
      Done
    case e: UpdateFailure[_] =>
      throw new IllegalStateException("Unexpected failure: " + e)
  }
}