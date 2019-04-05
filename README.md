# Akka gRPC ddata shopping cart

This is a shopping cart implemented using Akka distributed data (CRDTs). It takes the [Akka Distributed Data Highly Available Shopping Cart](https://github.com/akka/akka-samples/blob/2.5/akka-sample-distributed-data-scala/src/main/scala/sample/distributeddata/ShoppingCart.scala), and places a gRPC interface in front of it.

It configures the cluster formation using instructions found at the [Lightbend OpenShift deployment guide](https://developer.lightbend.com/guides/openshift-deployment/akka/forming-a-cluster.html).

## Running

If you wish to run the service in the `default` namespace, then simply run the following on your Kubernetes cluster:

```
kubectl apply -f https://raw.githubusercontent.com/lightbend/akka-grpc-ddata-shopping-cart/master/shopping-cart.yaml -n default
```

Otherwise, you will need to modify the RBAC role binding in the spec to be the service account to match the namespace you're using, for example, if your namespace is `myproject` (the default OpenShift namespace), modify the `User` subject to be:

```yaml
subjects:
- kind: "User"
  name: "system:serviceaccount:myproject:default"
```

Then you can apply the resulting spec.

This will start an Akka cluster of 3 nodes, and expose it as a service running on port 80. To find out the IP address of the service:

```
kubectl get svc shopping-cart
```

## Using

The gRPC protobuf spec can be found [here](src/main/protobuf/shoppingcart.proto). You can build a client to access it in your favourite language.

A node based client is provided as a helper script that can be loaded into the node REPL for ad-hoc testing. To use it:

```
cd client
npm install
node
```

Then in node, run:

```
.load main.js
```

to load the client helper. That will output a usage information instructions for how to use the client.

## Running in development

To start a two node cluster in development, run:

```
sbt -Dseed.node.ports=2552,2553 -Dremoting.port=2552 -Dhttp.port=8000 run
```

And this in another, to start a second node for the cluster:

```
sbt -Dseed.node.ports=2552,2553 -Dremoting.port=2553 -Dhttp.port=8001 run
```

## License

This software is licensed under the Apache 2 license.

## Maintenance notes

**This project is NOT supported under the Lightbend subscription.**

The project is maintained by the Lightbend Office of the CTO.
