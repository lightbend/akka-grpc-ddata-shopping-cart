package com.example.shoppingcart

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ddata.{DistributedData, LWWMap, LWWMapKey}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.example.shoppingcart.grpc.{Cart, LineItem}

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Adapted from https://github.com/akka/akka-samples/tree/2.5/akka-sample-distributed-data-scala#highly-available-shopping-cart
  *
  * The major change is that it's ask pattern based rather than using an actor, because Akka HTTP works better with
  * futures than actors.
  */
object ShoppingCartStore {

  import akka.cluster.ddata.Replicator._

  private val timeout = 3.seconds
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

}

class ShoppingCartStore(system: ActorSystem) {

  import ShoppingCartStore._
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
          Future.successful(Cart(data.entries.values.toList))
        case NotFound(_, _) =>
          Future.successful(Cart(Nil))
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
      case Some(LineItem(_, _, existingQuantity)) =>
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

  def getAndWatch(userId: String): Source[LineItem, NotUsed] = {
    val key = dataKey(userId)

    // Convenience rather than using a tuple for better readability. This class holds the state
    // of the scan operation used below, holding the current shopping cart map, as well as the
    // list of items to emit next.
    case class ScanState(current: Map[String, LineItem], emitNext: immutable.Seq[LineItem])

    Source.actorRef[Any](1, OverflowStrategy.dropHead)
      .mapMaterializedValue { ref =>
        // Now subscribe to updates - new subscribers always get the current value when they
        // first subscribe. Since we get the entire cart on each change and then work out
        // what's different to emit differences, there's no point in buffering more
        // than the most recent change, so we use a buffer size of one and drop any old
        // messages when it's full.
        replicator ! Subscribe(key, ref)
        NotUsed
      }
      // Take up to the deleted message, but include the deleted message so we can handle it
      .takeWhile(!_.isInstanceOf[Deleted[_]], inclusive = true)
      .scan(ScanState(Map.empty, Nil)) {

        // Handle the value changed
        case (ScanState(current, _), c@Changed(_)) =>
          val newData = c.get(key).entries
          // We collect any line items that either aren't in, or have changed from the old map
          val changedAndNewItems = newData.collect {
            case (productId, item) if !current.get(productId).contains(item) => item
          }.toList
          // We also find any line items that are in the old map, but not in the new, and emit
          // a new "deleted" message by setting quantity to 0.
          val deletedItems = (current -- newData.keys).map {
            case (_, item) => item.copy(quantity = 0)
          }
          ScanState(newData, changedAndNewItems ++ deletedItems)

        case (ScanState(current, _), Deleted) =>
          // If the cart is deleted, then simply output a delete for each of the items
          ScanState(Map.empty, current.values.map(_.copy(quantity = 0)).toList)

      }.mapConcat(_.emitNext)
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