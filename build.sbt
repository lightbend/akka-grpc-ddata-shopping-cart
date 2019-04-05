organization := "com.example"
name := "akka-grpc-ddata-shopping-cart"
scalaVersion := "2.12.8"

version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))

enablePlugins(JavaAppPackaging, DockerPlugin, AkkaGrpcPlugin, JavaAgent)

val AkkaVersion = "2.5.21"
val AkkaHttpVersion = "10.1.7"
val AkkaManagementVersion = "1.0.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion
)

javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"

dockerBaseImage := "adoptopenjdk/openjdk8"
dockerUpdateLatest := true
dockerRepository := sys.props.get("docker.registry")
dockerUsername := sys.props.get("docker.username")
dockerExposedPorts := Seq(9000, 8558, 2552)

// Used for when running on the command line
fork in run := true

val seedNodePorts = sys.props.get("seed.node.ports")
  .map(_.split(",").toList)
  .getOrElse(Nil)
val remotingPort = sys.props.get("remoting.port").getOrElse("0")
val httpPort = sys.props.get("http.port").getOrElse("0")

javaOptions in run ++= {
  seedNodePorts.zipWithIndex.map {
    case (port, index) => s"-Dakka.cluster.seed-nodes.$index=akka.tcp://default@127.0.0.1:$port"
  } :+ s"-Dakka.remote.netty.tcp.port=$remotingPort" :+ s"-Dhttp.port=$httpPort" :+ s"-Dakka.remote.netty.tcp.hostname=127.0.0.1"
}
