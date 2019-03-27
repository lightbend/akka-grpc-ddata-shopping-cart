package com.example.shoppingcart

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpConnectionContext, UseHttp2}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.ActorMaterializer
import com.example.shoppingcart.grpc.ShoppingCartServiceHandler

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * Adapted from https://doc.akka.io/docs/akka-http/current/introduction.html#routing-dsl-for-http-servers
  */
object ShoppingCartServer extends App {

  // needed to run the route
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  try {

    val httpPort = system.settings.config.getInt("http.port")
    val store = new ShoppingCartStore(system)

    Http().bindAndHandleAsync(
      ShoppingCartServiceHandler(new ShoppingCartServiceImpl(store)),
      interface = "127.0.0.1",
      port = httpPort,
      connectionContext = HttpConnectionContext(http2 = UseHttp2.Always))

    // Bind service handler servers to localhost:8080/8081

    if (!system.settings.config.getBoolean("dev")) {
      AkkaManagement(system).start()
      ClusterBootstrap(system).start()
    }
    println(s"Server online at http://127.0.0.1:$httpPort/")

    scala.sys.addShutdownHook {
      system.terminate()
      Await.result(system.whenTerminated, 30.seconds)
    }
  }
  catch {
    case NonFatal(e) =>
      e.printStackTrace()
      scala.sys.exit(1)
  }
}
