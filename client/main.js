var PROTO_PATH = '../src/main/protobuf/shoppingcart.proto';
var grpc = require('grpc');
var protoLoader = require('@grpc/proto-loader');
// Suggested options for similarity to existing grpc.load behavior
var packageDefinition = protoLoader.loadSync(
    PROTO_PATH,
    {keepCase: true,
     longs: String,
     enums: String,
     defaults: true,
     oneofs: true
    });
var protoDescriptor = grpc.loadPackageDefinition(packageDefinition);
// The protoDescriptor object has the full package hierarchy
var shoppingcart = protoDescriptor.shoppingcart;

function createClient(uri) {
  return new shoppingcart.ShoppingCartService(uri, grpc.credentials.createInsecure());
}

function render(item) {
  return item.name + " (" + item.productId + "): " + item.quantity;
}

function getCart(client, userId) {
  client.getCart({userId: userId}, function(err, cart) {
    if (err) {
      console.log(err);
    } else {
      console.log("Got cart with the following items:");
      cart.items.forEach(function(item) {
        console.log(render(item));
      });
    }
  })
}

function addItem(client, userId, productId, name, quantity) {
  client.addItem({userId: userId, item: {productId: productId, name: name, quantity: quantity}}, function(err) {
    if (err) {
      console.log(err);
    } else {
      console.log("Added.");
    }
  })
}

function deleteItem(client, userId, productId) {
  client.deleteItem({userId: userId, productId: productId}, function(err) {
    if (err) {
      console.log(err);
    } else {
      console.log("Deleted.");
    }
  })
}

function watchCart(client, userId) {
  var call = client.getAndWatchCart({userId: userId});
  call.on("end", function() {
    console.log("Watch terminated.");
  });
  call.on("error", function(e) {
    console.log("Error watching item:");
    console.log(e);
  });
  call.on("data", function(item) {
    console.log("Watch got item: " + render(item));
  });
  return {
    end: function() {
      call.end();
    }
  }
}

console.log("This is a helper for interacting the the shopping cart app.");
console.log("The first thing you will want to do is create a client, preferably one per node that you're running:");
console.log("");
console.log("var client0 = createClient('localhost:8000');");
console.log("var client1 = createClient('localhost:8001');");
console.log("");
console.log("Now, these clients are gRPC clients that can be used directly, or you can use the helper functions");
console.log("provided here. The helper functions invoke a gRPC method, and attach a callback that logs either");
console.log("errors, or the result, asynchronously.");
console.log("So, here's adding some items to a cart, then getting the cart:");
console.log("");
console.log("addItem(client0, 'mycart', '10000', 'Bike', 2)");
console.log("addItem(client0, 'mycart', '10001', 'Laptop', 1)");
console.log("getCart(client0, 'mycart')");
console.log("");
console.log("One of the more interesting things to try is watching a cart, which uses gRPC streaming backed by");
console.log("Akka Distributed Data subscriptions - see what happens when you subscribe on a different node to");
console.log("the node where the updates are made:");
console.log("");
console.log("var watch = watchCart(client0, 'mycart')");
console.log("addItem(client1, 'mycart', '10002', 'Phone', 1)");
console.log("addItem(client1, 'mycart', '10001', 'Laptop', 2)");
console.log("");
console.log("You could also try stopping nodes, seeing how the app responds, etc.");


