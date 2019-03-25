package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import ShoppingCart._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import spray.json.{DefaultJsonProtocol, JsNumber, JsValue, RootJsonFormat}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Adapted from https://doc.akka.io/docs/akka-http/current/introduction.html#routing-dsl-for-http-servers
  */
object ShoppingCartServer extends App {

  // needed to run the route
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  // formats for unmarshalling and marshalling
  implicit val itemFormat = jsonFormat2(LineItem)
  implicit val cartFormat = jsonFormat1(Cart)

  implicit object RootJsIntFormat extends RootJsonFormat[Int] {
    def write(value: Int) = JsNumber(value)
    def read(value: JsValue) = value.convertTo[Int](DefaultJsonProtocol.IntJsonFormat)
  }

  val shoppingCart = new ShoppingCart(system)

  val route: Route =
    pathPrefix("cart" / Segment) { userId =>
      pathEnd {
        get {
          onSuccess(shoppingCart.getCart(userId)) { cart =>
            complete(cart)
          }
        }
      } ~
      path("product" / Segment) { productId =>
        put {
          entity(as[Int]) { quantity =>
            onSuccess(shoppingCart.addItem(userId, LineItem(productId, quantity))) { _ =>
              complete("Item added")
            }
          }
        } ~
        delete {
          onSuccess(shoppingCart.removeItem(userId, productId)) { _ =>
            complete("Item removed")
          }
        }
      }
    }

  try {
    val httpPort = system.settings.config.getInt("http.port")
    val bindingFuture = Http().bindAndHandle(route, "127.0.0.1", httpPort)
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    println(s"Server online at http://127.0.0.1:$httpPort/")

    scala.sys.addShutdownHook {
      system.terminate()
      Await.result(system.whenTerminated, 30.seconds)
    }
  } catch {
    case e =>
      e.printStackTrace()
      scala.sys.exit(1)
  }
}
