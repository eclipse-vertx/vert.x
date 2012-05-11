# Http Performance example

Consists of a server, a client and a rate counter (written in Java)

The server simply responds OK to every request (or serves a static file, see code)

The client creates X connections and repeatedly makes HTTP requests as long as it has enough credits. It starts off with Y credits, and a credit is removed each time a request is made and given back every time a response comes back. This is therefore a basic form of flow control.

Every Z requests, the client sends a message to the rate counter on the event bus saying it has sent Z requests.

The rate counter just sits on the event bus and displays rate statistics, as it receives messages.

The client and rate counter therefore need to be clustered since they communicate on the event bus.

## How to run stuff:

(Run everything from the parent directory of this directory).

Run the rate counter in a console (give it a unique cluster port)

`vertx run org.vertx.java.examples.perf.RateCounter -cp classes -cluster -cluster-port 25501`

Run the server in another console (give it 6 instances to utilise the cores on the server)

`vertx run org.vertx.java.examples.httpperf.PerfServer -cp classes -instances 6`

Run the client in another console:

`vertx run org.vertx.java.examples.httpperf.PerfClient -cp classes -instances 6 -cluster`

Of course, you can run client and server on different machines. You'll probably need a fast network to avoid getting IO bound.

To compare it with the equivalent node.js server, instead of running the vert.x server run the node.js server as follows:

`node nodejs-server.js`

and compare the performance.

You can run a clustered node.js too:

`node nodejs-cluster-server.js`

Also.. you can run a vert.x JavaScript server instead of a Java one if you like:

`vertx run vertx-server.js -instances 8`

or the Ruby server:

`vertx run vertx_server.rb -instances 8`

or the Groovy server:

`vertx run vertx_server.groovy -instances 8`
