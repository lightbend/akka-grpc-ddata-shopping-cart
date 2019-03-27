# Akka ddata shopping cart KNative experiment

The goal of this project is to get an Akka Cluster working in KNative.

This basically takes the [Akka Distributed Data Highly Available Shopping Cart](https://github.com/akka/akka-samples/blob/2.5/akka-sample-distributed-data-scala/src/main/scala/sample/distributeddata/ShoppingCart.scala), and places an Akka HTTP interface in front of it.

It configures the cluster formation using instructions found at the [Lightbend OpenShift deployment guide](https://developer.lightbend.com/guides/openshift-deployment/akka/forming-a-cluster.html), but with additional changes as described in [this issue](https://github.com/akka/akka-management/issues/209), which includes:

* Always bind to `127.0.0.1`
* Use the pod service name for communication between pods
* Configure `ServiceEntry`'s for intra-pod communication
* Don't specify health checks as these will prevent communication prior to readiness
* Modify docker entry point to set the pod ip address to be a dash separated address for the purposes of constructing pod service names in configuration

## Downing

Downing hasn't been addressed yet. It's CRDTs, and it does attempt to do quorum reads, with fallback to local reads if that fails, so we could just use auto downing, though that's not ideal.

One major problem at the moment is I think when a pod is stopped, both the sidecar and the pod are stopped simultaneously, and because the sidecar stops, this means that communication via Akka Remoting will likely fail before the node has a chance to gracefully leave the cluster, so every scale down or rolling upgraded does non graceful stops and requires a downing strategy. I'm not sure if there is any way around this, I can't see anything in Kubernetes that allows controlling the order of container shutdown within a pod. This might be a good reason to have Akka remoting traffic bypass the Istio sidecar - if that were possible.

## Running in Minikube

So far, I have managed to get the project running on Minikube with Istio (I haven't yet attempted to run in KNative).

To get to where I've got, follow the instructions for installing Knative on Minikube up to Istio:

https://www.knative.dev/docs/install/knative-with-minikube/#installing-istio

Now build and deploy the docker image:

```
eval $(minikube docker-env)
sbt docker:publishLocal
```

And deploy the app:

```
kubectl apply -f shopping-cart.yaml
```

You should see two pods start, and if you check their logs, you should see them form a cluster with each other. The instructions I used to get to this point come from here:

https://github.com/akka/akka-management/issues/209

To actually test out the service, set up the gateway (at this point I've got no idea what I'm doing, could be all wrong):

```
kubectl apply -f istio-ingress.yaml
```

Now if you run

```
$ minikube service istio-ingressgateway -n istio-system --url
http://192.168.39.149:31380
http://192.168.39.149:31390
http://192.168.39.149:31400
http://192.168.39.149:30092
http://192.168.39.149:30053
http://192.168.39.149:30318
http://192.168.39.149:31580
http://192.168.39.149:32354
```

You can see a bunch of URLs. One of them will be the correct URL for accessing the shopping cart. To know which, run:

```
$ kubectl get svc istio-ingressgateway -n istio-system
NAME                   TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)                                                                                                                   AGE
istio-ingressgateway   NodePort   10.105.81.196   <none>        80:31380/TCP,443:31390/TCP,31400:31400/TCP,15011:30092/TCP,8060:30053/TCP,853:30318/TCP,15030:31580/TCP,15031:32354/TCP   173m
```

## gRPC client

To use the gRPC client, switch to the client directory, and run:

```
npm install
node
```

In node, run:

```
.load main.js
```

To load the client helper. That will output a usage information instructions for how to use the client.

This can be used in development by running this is one terminal:

```
sbt -Dseed.node.ports=2552,2553 -Dremoting.port=2552 -Dhttp.port=8000 run
```

And this in another, to start a second node for the cluster:

```
sbt -Dseed.node.ports=2552,2553 -Dremoting.port=2553 -Dhttp.port=8001 run
```

## Next steps

Try and run the above but in KNative.
