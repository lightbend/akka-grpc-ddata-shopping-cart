package com.example.shoppingcart

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.example.shoppingcart.grpc._

import scala.concurrent.{ExecutionContext, Future}

class ShoppingCartServiceImpl(store: ShoppingCartStore)(implicit ec: ExecutionContext) extends ShoppingCartService {

  override def getCart(request: CartRequest): Future[Cart] = store.getCart(request.userId)

  override def getAndWatchCart(request: CartRequest): Source[LineItem, NotUsed] = store.getAndWatch(request.userId)

  override def addItem(request: AddItemRequest): Future[Empty] = {
    store.addItem(request.userId, request.item.getOrElse(throw new IllegalArgumentException("item is required")))
      .map(_ => Empty())
  }

  override def deleteItem(request: DeleteItemRequest): Future[Empty] =
    store.removeItem(request.userId, request.productId).map(_ => Empty())
}
