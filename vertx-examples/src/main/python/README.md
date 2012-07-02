# vert.x Python Examples

Prerequisites:

1) The bin directory from the distro must be on your PATH - this should have been done as part of the install procedure.

2) JDK/JRE 1.7.0+ must be installed and the JDK/JRE bin directory must be on your PATH

(The instructions below use the `vertx run` command to start each vertx application in its own vert.x instance, alternatively
you can run vertx is application server mode. See the wiki for more information).

3) Run an example:

(for full help on deploying just type vertx from the command line)

(all examples should be run from this directory unless otherwise stated (e.g. the webapp example))

vertx run <example script name>

where <example script name> is, for example, echo/echo_server.py

There now follows a description of all the available examples:

## Echo Server + Client

A simple echo server which echos back any sent to it

To run the server:

vertx run echo/echo_server.py

Then either telnet localhost 1234 and notice how text entered via telnet is echoed back

Instead of telnet you can also run a simple echo client in a different console:

vertx run echo/echo_client.py

## Fanout Server

Fans out all data received on any one connection to all other connections.

To run the server:

vertx run fanout/fanout_server.py

Then telnet localhost 1234 from different consoles. Note how data entered in telnet is echoed to all connected connections

## HTTP

A simple HTTP server which just returns some hard-coded HTML to the client, and a simple HTTP client which sends a GET
request and displays the response it receives.

To run the server:

vertx run http/http_server.py

Then point your browser at http://localhost:8080

Alternatively, you can also run the HTTP client from a different console:

vertx run http/http_client.py

## HTTPS

Like the HTTP example, but using HTTPS

To run the server:

vertx run https/https_server.py

Then point your browser at https://localhost:4443

Alternatively, you can also run the HTTPS client from a different console:

vertx run https/https_client.py

You'll get a warning from your browser since the server certificate the server is using is not known to it, that's normal.

## Proxy

A very simple HTTP proxy which simply proxies requests/response from a client to a server and back again.
Run each part in its own console:

It includes

a) A simple http server which just takes the request and sends back a response in 10 chunks

b) A simple http client which sends a http request with 10 chunks (via the proxy server), and displays any
response it receives

c) A proxy server which simply sits in the middle proxying requests and responses between client and server

Run the http server:

vertx run proxy/http_server.py

Run the proxy server:

vertx run proxy/proxy_server.py

Run the http client:

vertx run proxy/http_client.py

## PubSub

A very simple publish-subscribe server.

Connections can subscribe to topics and unsubscribe from topics. Topics can be any arbitrary string.

When subscribed, connections will receive any messages published to any of the topics it is subscribed to.

The pub-sub server understands the following simple text protocol. Each line is terminated by CR (hit enter on telnet)

To subscribe to a topic:

subscribe,<topic_name>

To unsubscribe from a topic:

unsubscribe,<topic_name>

To publish a message to a topic:

publish,<topic_name>,<message>

Where:

<topic_name> is the name of a topic

<message is some string you want to publish

To run the server:

vertx run -pubsub/pubsub_server.py

Then open some more consoles and telnet localhost 1234, and experiment with the protocol.

## SendFile

Simple web server that uses sendfile to serve content directly from disk to the socket bypassing user space. This is a
very efficient way of serving static files from disk.

The example contains three static pages: index.html, page1.html and page2.html which are all served using sendfile.

To run the server:

vertx run sendfile/sendfile.py

Then point your browser at http://localhost:8080 and click around

## SSL

This is like the echo example, but this time using SSL.

To run the server:

vertx run -ssl/ssl_server.py

To run the client in a different console:

vertx run ssl/ssl_client.py

## Upload

A simple upload server example. The client streams a file from disk to an HTTP request and the server reads the
HTTP request and streams the data to a file on disk.

To run the server:

vertx run upload/upload_server.py

To run the client in a different console:

vertx run upload/upload_client.py

## Websockets

A simple example demonstrating HTML 5 websockets. The example serves a simple page which has some JavaScript in it
to create a websocket to a server, and send and receive data from it.

The server just echoes back any data is receives on the websocket.

To run the server:

vertx run websockets/websockets.py

Then point your browser at: http://localhost:8080

## Route Match

This example shows how a route matcher can be used with a vert.x HTTP server to allow REST-style resource based matching of URIS
in the manner of express (JS) or Sinatra.

To run the example:

vertx run route_match/route_match_example.py

Then point your browser at: http://localhost:8080.

An index page will be served which contains some links to urls of the form:

/details/<user>/<id>

The server will extract the user and id from the uri and display it on the returned page

## SockJS

A simple example demonstrating SockJS connections from a browser. The example serves a simple page which has some JavaScript in it
to create a SockJS connection to a server, and send and receive data from it.

To run the server:

vertx run sockjs/sockjs.py

Then point your browser at: http://localhost:8080

## Eventbus Bridge

This example shows how the vert.x event bus can extend to client side JavaScript.

To run the server:

vertx run eventbusbridge/bridge_server.py

The example shows a simple publish / subscribe client side JavaScript application that uses the vert.x event bus.

Using the application you can subscribe to one or more "addresses", then send messages to those addresses.

To run it, open one or more browsers and point them to http://localhost:8080.

First connect, then try subscribing and sending messages and see how the separate browsers can interoperate on the event bus.

## Web application

This is a full end-end "real-time" web appplication which has a modern JavaScript client side MVVM application that communicates via the event bus with a persistor.

It's the same application from the tutorial.

You will need an instance of MongoDB server (with default settings) running on localhost.

To run it simply `cd` into the webapp directory and `vertx run app.py`

To see log output set the following in `conf/logging.properties`

    org.vertx.level=FINE

Then point your browser at https://localhost:8080 and start shopping! (Note it's https not http!)

To fully understand this example please follow the web app tutorial on the website





