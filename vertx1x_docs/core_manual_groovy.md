<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->
[TOC]

# Writing Verticles

We previously discussed how a verticle is the unit of deployment in vert.x. Let's look in more detail about how to write a verticle.

As an example we'll write a simple TCP echo server. The server just accepts connections and any data received by it is echoed back on the connection.

Copy the following into a text editor and save it as `Server.groovy`

    import static org.vertx.groovy.core.streams.Pump.createPump

    vertx.createNetServer().connectHandler { socket ->
      createPump(socket, socket).start()
    }.listen(1234)

Now run it:

    vertx run Server.groovy

The server will now be running. Connect to it using telnet:

    telnet localhost 1234

And notice how data you send (and hit enter) is echoed back to you.

Congratulations! You've written your first verticle.

## Verticle clean-up

Servers, clients and event bus handlers will be automatically closed when the verticle is stopped. However, if you have any other clean-up logic that you want to execute when the verticle is stopped,
you can implement a `vertxStop` top level method which will be called when the verticle is undeployed.

## Getting Configuration in a Verticle

If JSON configuration has been passed when deploying a verticle from either the command line using `vertx run` or `vertx deploy` and specifying a configuration file, or when deploying programmatically, that configuration is available in the `config` property of the `container` variable which is injected into the top level script. The config is a map which represents the JSON config.

    def config = container.config


You can use this config to configure the verticle. Allowing verticles to be configured in a consistent way like this allows configuration to be easily passed to them irrespective of the language.

## Logging from a Verticle

Each verticle is given its own logger. To get a reference to it use the `logger` property on the container instance:

    def logger = container.logger

    logger.info "I am logging something"

The logger is an instance of the class `org.vertx.core.logging.Logger` and has the following methods;

* trace
* debug
* info
* warn
* error
* fatal

Which have the normal meanings you would expect.

The log files by default go in a file called `vertx.log` in the system temp directory. On my Linux box this is `\tmp`.

For more information on configuring logging, please see the main manual.

## Accessing environment variables from a Verticle

You can access the map of environment variables from a Verticle with the `getEnv()` method on the `container` object.

# Deploying and Undeploying Verticles Programmatically

You can deploy and undeploy verticles programmatically from inside another verticle. Any verticles deployed programmatically inherit the path of the parent verticle.

## Deploying a simple verticle

To deploy a verticle programmatically call the method `deployVerticle` on the `container` variable. The return value of `deployVerticle` is the unique id of the deployment, which can be used later to undeploy the verticle.

To deploy a single instance of a verticle:

    def id = container.deployVerticle(main)

Where `main` is a String that represents the name of the "main" of the Verticle (i.e. the name of the script if it's a Ruby, Groovy or JavaScript verticle (e.g. my_script.rb or MyScript.js or MyScript.groovy) or the fully qualified class name if it's a Java verticle). See the chapter on "running vert.x" in the main manual for a description of what a main is.

## Deploying a module programmatically

You should use `deployModule` to deploy a module, for example:

    container.deployModule("vertx.mailer-v1.0", config)
    
Would deploy an instance of the `vertx.mailer` module with the specified configuration. Please see the modules manual
 for more information about modules.


## Passing configuration to a verticle programmatically

JSON configuration can be passed to a verticle that is deployed programmatically. For example:

    def config = [ foo: "wibble", bar: false]

    container.deployVerticle("foo.ChildVerticle", config)

Then, in `ChildVerticle` you can access the config via the `config` property as previously explained.

## Using a Verticle to co-ordinate loading of an application

If you have an application that is composed of multiple verticles that all need to be started at application start-up, then you can use another verticle that maintains the application configuration and starts all the other verticles. You can think of this as your application starter verticle.

For example, you could create a verticle `app.groovy` as follows:

    // Application config

    def appConfig = [
        verticle1Config: [
            // Config for verticle1
        ],
        verticle2Config: [
            // Config for verticle2
        ],
        verticle3Config: [
            // Config for verticle3
        ],
        verticle4Config: [
            // Config for verticle4
        ],
        verticle5Config: [
            // Config for verticle5
        ]
    ]

    // Start the verticles that make up the app

    container.deployVerticle("verticle1.js", appConfig["verticle1Config"])
    container.deployVerticle("verticle2.rb", appConfig["verticle2Config"])
    container.deployVerticle("foo.Verticle3", appConfig["verticle3Config"])
    container.deployWorkerVerticle("foo.Verticle4", appConfig["verticle4Config"])
    container.deployWorkerVerticle("verticle5.js", appConfig["verticle5Config"], 10)

Then you can start your entire application by simply running:

    vertx run app.groovy

or

    vertx deploy app.groovy

See the chapter on running vert.x in the main manual for more information on this.

## Specifying number of instances

By default, when you deploy a verticle only one instance of the verticle is deployed. If you want more than one instance to be deployed, e.g. so you can scale over your cores better, you can specify the number of instances as follows:

    container.deployVerticle("foo.ChildVerticle", 10)

The above example would deploy 10 instances.

## Getting Notified when Deployment is complete

The actual verticle deployment is asynchronous and might not complete until some time after the call to `deployVerticle` has returned. If you want to be notified when the verticle has completed being deployed,
you can pass a Closure as the final argument to `deployVerticle`:

    container.deployVerticle("foo.ChildVerticle", 10) {
        println "The verticle has been deployed"
    }

## Deploying Worker Verticles

The `deployVerticle` method deploys standard (non worker) verticles. If you want to deploy worker verticles use the `deployWorkerVerticle` method. This method takes the same parameters as `deployVerticle` with the same meanings.

## Undeploying a Verticle

Any verticles that you deploy programmatically from within a verticle, and all of their children are automatically undeployed when the parent verticle is undeployed, so in most cases you will not need to undeploy a verticle manually, however if you do want to do this, it can be done by calling the method `undeployVerticle` passing in the deployment id that was returned from the call to `deployVerticle`

    def deploymentID = container.deployVerticle(main)

    container.undeployVerticle(deploymentID)


# The Event Bus

The event bus is the nervous system of vert.x.

It allows verticles to communicate with each other irrespective of what language they are written in, and whether they're in the same vert.x instance, or in a different vert.x instance. It even allows client side JavaScript running in a browser to communicate on the same event bus. (More on that later).

It creates a distributed polyglot overlay network spanning multiple server nodes and multiple browsers.

The event bus API is incredibly simple. It basically involves registering handlers, unregistering handlers and sending, or publishing, messages.

First some theory:

## The Theory

### Addressing

Messages are sent on the event bus to an *address*.

Vert.x doesn't bother with any fancy addressing schemes. In vert.x an address is simply a string, any string is valid. However it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.

Some examples of valid addresses are `europe.news.feed1`, `acme.games.pacman`, `sausages`, and `X`.

### Handlers

A handler is a thing that receives messages from the bus. You register a handler at an address.

Many different handlers from the same or different verticles can be registered at the same address. A single handler can be registered by the verticle at many different addresses.

### Publish / subscribe messaging

The event bus supports *publishing* messages. Messages are published to an address. Publishing means delivering the message to all handlers that are registered at that address. This is the familiar *publish/subscribe* messaging pattern.

### Point to point messaging

The event bus supports *point to point* messaging. Messages are sent to an address. This means a message is delivered to *at most* one of the handlers registered at that address. If there is more than one handler regsitered at the address, one will be chosen using a non-strict round-robin algorithm.

With point to point messaging, an optional reply handler can be specified when sending the message. When a message is received by a recipient, and has been *processed*, the recipient can optionally decide to reply to the message. If they do so that reply handler will be called.

When the reply is received back at the sender, it too can be replied to. This can be repeated ad-infinitum, and allows a dialog to be set-up between two different verticles. This is a common messaging pattern called the *Request-Response* pattern.

### Transient

*All messages in the event bus are transient, and in case of failure of all or parts of the event bus, there is a possibility messages will be lost. If your application cares about lost messages, you should code your handlers to be idempotent, and your senders to retry after recovery.*

If you want to persist your messages you can use a persistent work queue module for that.

### Types of messages

Messages that you send on the event bus can be as simple as a string, a number or a boolean. You can also send vert.x buffers or JSON messages.

It's highly recommended you use JSON messages to communicate between verticles. JSON is easy to create and parse in all the languages that vert.x supports.

## Event Bus API

Let's jump into the API.

To get a reference to the event bus use the `eventBus` property on the `vertx` instance that is injected into the
verticle script (or that you created if running embedded).

### Registering and Unregistering Handlers

To set a message handler on the address `test.address`, you specify the handler as a Closure to the `registerHandler`
 method.

    def eb = vertx.eventBus

    eb.registerHandler("test.address") { message -> println "I received a message ${message.body}" }

The return value of `registerHandler` is a unique id for the handler that you can later use when unregistering, if you like.

When you register a handler on an address and you're in a cluster it can take some time for the knowledge of that new handler to be propagated across the entire cluster. If you want to be notified when that has completed you can optionally specify another handler to the `registerHandler` method as the third argument. This handler will then be called once the information has reached all nodes of the cluster. E.g. :

    def myHandler = { message -> println "I received a message ${message.body}" }

    eb.registerHandler("test.address", myHandler) { println "The handler has been registered across the cluster" }

To unregister a handler it's just as straightforward. You simply call `unregisterHandler` passing in the address and the handler:

    eb.unregisterHandler("test.address", myHandler)

A single handler can be registered multiple times on the same, or different, addresses so in order to identify it uniquely you have to specify both the address and the handler.

Alternatively, you can unregister a handler using a unique id that was returned from the call to `registerHandler`.

As with registering, when you unregister a handler and you're in a cluster it can also take some time for the knowledge of that unregistration to be propagated across the entire to cluster. If you want to be notified when that has completed you can optionally specify another handler to the registerHandler as the third argument. E.g. :

    eb.unregisterHandler("test.address", myHandler) { println "The handler has been unregistered across the cluster" }

If you want your handler to live for the full lifetime of your verticle there is no need to unregister it explicitly - vert.x will automatically unregister any handlers when the verticle is stopped.

### Publishing messages

Publishing a message is also trivially easy. Just publish it specifying the address, for example:

    eb.publish("test.address", "hello world")

That message will then be delivered to any handlers registered against the address "test.address".

### Sending messages

Sending a message will result in at most one handler registered at the address receiving the message. This is the point to point messaging pattern.

    eb.send("test.address", "hello world")

### Replying to messages

Sometimes after you send a message you want to receive a reply from the recipient. This is known as the *request-response pattern*.

To do this you send a message, and specify a reply handler as the third argument. When the receiver receives the message they can reply to it by calling the `reply` method on the message. When this method is invoked it causes a reply to be sent back to the sender where the reply handler is invoked. An example will make this clear:

The receiver:

    def myHandler = { message ->
        println "I received a message ${message.body}"

        message.reply "This is a reply"  // Reply to it
    }

    eb.registerHandler("test.address", myHandler)

The sender:

    eb.send("test.address", "This is a message") { message ->
        println "I received a reply ${message.body}"
    }

It is legal also to send an empty reply or a null reply.

The replies themselves can also be replied to so you can create a dialog between two different verticles consisting of multiple rounds.

### Message types

The message you send can be any of the following types (or their matching boxed type):

* boolean
* byte[]
* byte
* char
* double
* float
* int
* long
* short
* java.lang.String
* java.util.Map - representing a JSON object
* org.vertx.java.groovy.buffer.Buffer

Vert.x buffers and JSON objects are copied before delivery if they are delivered in the same JVM, so different verticles can't access the exact same object instance.

Here are some more examples:

Send some numbers:

    eb.send("test.address", 1234)
    eb.send("test.address", 3.14159)

Send a boolean:

    eb.send("test.address", true)

Send a JSON object:

    eb.send("test.address", ["foo": "wibble", "bar": "eek"])

Null messages can also be sent:

    eb.send("test.address", null)

It's a good convention to have your verticles communicating using JSON.

## Distributed event bus

To make each vert.x instance on your network participate on the same event bus, start each vert.x instance with the `-cluster` command line switch.

See the chapter in the main manual on *running vert.x* for more information on this.

Once you've done that, any vert.x instances started in cluster mode will merge to form a distributed event bus.

# Shared Data

Sometimes it makes sense to allow different verticles instances to share data in a safe way. Vert.x allows simple `java.util.concurrent.ConcurrentMap` and `java.util.Set` data structures to be shared between verticles.

There is a caveat: To prevent issues due to mutable data, vert.x only allows simple immutable types such as number, boolean and string or Buffer to be used in shared data. With a Buffer, it is automatically copied when retrieved from the shared data, so different verticle instances never see the same object instance.

Currently data can only be shared between verticles in the *same vert.x instance*. In later versions of vert.x we aim to extend this to allow data to be shared by all vert.x instances in the cluster.

## Shared Maps

To use a shared map to share data between verticles first we get a reference to the map, and then use it like any other instance of `java.util.concurrent.ConcurrentMap`

    def map = vertx.sharedData.getMap('demo.mymap')

    map["some-key"] = 123

And then, in a different verticle you can access it:

    def map = vertx.sharedData.getMap('demo.mymap')

    // etc


## Shared Sets

To use a shared set to share data between verticles first we get a reference to the set.

    def set = vertx.sharedData.getMap('demo.myset')

    set << "some-value"

And then, in a different verticle:

    def set = vertx.sharedData.getMap('demo.myset')

    // etc

# Buffers

Most data in vert.x is shuffled around using instances of `org.vertx.groovy.core.buffer.Buffer`.

A Buffer represents a sequence of zero or more bytes that can be written to or read from, and which expands automatically as necessary to accomodate any bytes written to it. You can perhaps think of a buffer as smart byte array.

## Creating Buffers

Create a new empty buffer:

    def buff = new Buffer()

Create a buffer from a String. The String will be encoded in the buffer using UTF-8.

    def buff = new Buffer("some-string")

Create a buffer from a String: The String will be encoded using the specified encoding, e.g:

    def buff = new Buffer("some-string", "UTF-16")

Create a buffer from a byte[]

    def bytes = ...
    def buff = new Buffer(bytes)

Create a buffer with an initial size hint. If you know your buffer will have a certain amount of data written to it you can create the buffer and specify this size. This makes the buffer initially allocate that much memory and is more efficient than the buffer automatically resizing multiple times as data is written to it.

Note that buffers created this way *are empty*. It does not create a buffer filled with zeros up to the specified size.

    def buff = new Buffer(100000)

## Writing to a Buffer

There are two ways to write to a buffer: appending, and random access. In either case buffers will always expand automatically to encompass the bytes. It's not possible to get an `IndexOutOfBoundsException` with a buffer.

### Appending to a Buffer

To append to a buffer, you use the `appendXXX` methods or the leftShift (<<) operator. Append methods exist for appending other buffers, byte[], String and all primitive types.

The return value of the `appendXXX` methods is the buffer itself, so these can be chained:

    def buff = new Buffer()

    // You can use the appendXXX methods

    buff.appendInt(123).appendString("hello").appendChar('\n')

    // Or use the leftShift operator

    buff << 123 << "hello" << '\n'

    socket << buff

### Random access buffer writes

You can also write into the buffer at a specific index, by using the `setXXX` methods. Set methods exist for other buffers, byte[], String and all primitive types. All the set methods take an index as the first argument - this represents the position in the buffer where to start writing the data.

The buffer will always expand as necessary to accomodate the data.

    def buff = new Buffer()

    buff.setInt(1000, 123)
    buff.setBytes(0, "hello")

You can also use the subscript operator to set data in the buffer at a specific index, e.g.

    def buff = new Buffer()

    buff[1000] = 123
    buff[0] = "hello"

## Reading from a Buffer

Data is read from a buffer using the `getXXX` methods. Get methods exist for byte[], String and all primitive types. The first argument to these methods is an index in the buffer from where to get the data.

    def buff = ...
    for (i in 0 ..< buff.length) {
        println "byte value at $i is ${buff.getByte(i)}"
    }

You can also use the subscript operator to get a byte

    def buff = ...
    for (i in 0 ..< buff.length) {
        println "byte value at $i is ${buff[i]}"
    }

## Other buffer methods:

* `length()`: To obtain the length of the buffer. The length of a buffer is the index of the byte in the buffer with the largest index + 1.
* `copy()`: Copy the entire buffer


See the JavaDoc for more detailed method level documentation.

# Delayed and Periodic Tasks

It's very common in vert.x to want to perform an action after a delay, or periodically.

In standard verticles you can't just make the thread sleep to introduce a delay, as that will block the event loop thread.

Instead you use vert.x timers. Timers can be *one-shot* or *periodic*. We'll discuss both

## One-shot Timers

A one shot timer calls an event handler after a certain delay, expressed in milliseconds.

To set a timer to fire once you use the `setTimer` method passing in the delay and a handler:

    vertx.setTimer(1000) { timerID -> println "And one second later this is displayed" }

    println "First this is printed"

The handler is passed the unique id for the timer which can be used to cancel the timer.

## Periodic Timers

You can also set a timer to fire periodically by using the `setPeriodic` method. There will be an initial delay equal to the period. The return value of `setPeriodic` is a unique timer id (long). This can be later used if the timer needs to be cancelled. The argument passed into the timer event handler is also the unique timer id:

    long timerID = vertx.setPeriodic(1000, new Handler<Long>() {
        public void handle(Long timerID) {
            log.info('And every second this is printed')
        }
    })

    log.info('First this is printed')

## Cancelling timers

To cancel a periodic timer, call the `cancelTimer` method specifying the timer id. For example:

    long timerID = vertx.setPeriodic(1000) {}

    // And immediately cancel it

    vertx.cancelTimer(timerID)

Or you can cancel it from inside the event handler. The following example cancels the timer after it has fired 10 times.

    def count = 0
    long timerID = vertx.setPeriodic(1000) { timerID ->
        println "In event handler $count"
        count++
        if (count == 10) {
            vertx.cancelTimer(timerID)
        }
    }

# Writing TCP Servers and Clients

Creating TCP servers and clients is incredibly easy with vert.x.

## Net Server

### Creating a Net Server

To create a TCP server you call the `createNetServer` method on your `vertx` instance.

    def server = vertx.createNetServer()

### Start the Server Listening

To tell that server to listen for connections we do:

    def server = vertx.createNetServer()

    server.listen(1234, "myhost")

The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If it is omitted it will default to `0.0.0.0` which means it will listen at all available interfaces.

### Getting Notified of Incoming Connections

Just having a TCP server listening creates a working server that you can connect to (try it with telnet!), however it's not very useful since it doesn't do anything with the connections.

To be notified when a connection occurs we need to call the `connectHandler` method of the server, passing in a handler. The handler will be called when a connection is made:

    def server = vertx.createNetServer()

    server.connectHandler { sock -> println "A client has connected!" }

    server.listen(1234, "localhost")

That's a bit more interesting. Now it displays 'A client has connected!' every time a client connects.

The return value of the `connectHandler` method is the server itself, so multiple invocations can be chained together. That means we can rewrite the above as:

    def server = vertx.createNetServer()

    server.connectHandler { sock -> println "A client has connected!" }.listen(1234, "localhost")

or

    vertx.createNetServer().connectHandler { sock -> println "A client has connected!" }.listen(1234, "localhost")


This is a common pattern throughout the vert.x API.


### Closing a Net Server

To close a net server just call the `close` method.

    server.close()

The close is actually asynchronous and might not complete until some time after the `close` method has returned. If you want to be notified when the actual close has completed then you can pass in a handler to the `close` method.

This handler will then be called when the close has fully completed.

    server.close { println "The server is now fully closed." }

If you want your net server to last the entire lifetime of your verticle, you don't need to call `close` explicitly, the Vert.x container will automatically close any servers that you created when the verticle is stopped.

### NetServer Properties

NetServer has a set of properties you can set which affect its behaviour. Firstly there are bunch of properties used to tweak the TCP parameters, in most cases you won't need to set these:

* `tcpNoDelay`: If `tcpNoDelay` is true then [Nagle's Algorithm](http://en.wikipedia.org/wiki/Nagle's_algorithm) is disabled. If false then it is enabled.

* `sendBufferSize`: Sets the TCP send buffer size in bytes.

* `receiveBufferSize`: Sets the TCP receive buffer size in bytes.

* `TCPKeepAlive`: if `keepAlive` is true then [TCP keep alive](http://en.wikipedia.org/wiki/Keepalive#TCP_keepalive) is enabled, if false it is disabled.

* `reuseAddress`: if `reuse` is true then addresses in TIME_WAIT state can be reused after they have been closed.

* `soLinger`

* `trafficClass`

NetServer has a further set of properties which are used to configure SSL. We'll discuss those later on.


### Handling Data

So far we have seen how to create a NetServer, and accept incoming connections, but not how to do anything interesting with the connections. Let's remedy that now.

When a connection is made, the connect handler is called passing in an instance of `NetSocket`. This is a socket-like interface to the actual connection, and allows you to read and write data as well as do various other things like close the socket.


#### Reading Data from the Socket

To read data from the socket you need to set the `dataHandler` on the socket. This handler will be called with an
instance of `org.vertx.groovy.core.buffer.Buffer` every time data is received on the socket. You could try the following code and telnet to it to send some data:

    def server = vertx.createNetServer()

    server.connectHandler { sock ->
        sock.dataHandler { buffer ->
            println "I received ${buffer.length} bytes of data"
        }
    }.listen(1234, "localhost")

#### Writing Data to a Socket

To write data to a socket, you invoke the `write` method or leftShift operator This method can be invoked in a few
ways:

With a single buffer:

    def myBuffer = new Buffer(...)
    sock.write(myBuffer)

    // Or

    sock << myBuffer

A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    sock.write("hello")

    // Or

    sock << "hello"

A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.

    sock.write("hello", "UTF-16")

The `write` method or leftShift is asynchronous and always returns immediately after the write has been queued.

The actual write might occur some time later. If you want to be informed when the actual write has happened you can pass in a handler as a final argument.

This handler will then be invoked when the write has completed:

    sock.write('hello') { println "It has actually been written" }

Let's put it all together.

Here's an example of a simple TCP echo server which simply writes back (echoes) everything that it receives on the socket:

    def server = vertx.createNetServer()

    server.connectHandler { sock ->
        sock.dataHandler { buffer -> sock << buffer }
    }.listen(1234, "localhost")

### Closing a socket

You can close a socket by invoking the `close` method. This will close the underlying TCP connection.

### Closed Handler

If you want to be notified when a socket is closed, you can set the `closedHandler':


    def server = vertx.createNetServer()

    server.connectHandler { sock -> 
        sock.closedHandler { println "The socket is now closed" }
    }.listen(1234, "localhost")


The closed handler will be called irrespective of whether the close was initiated by the client or server.

### Exception handler

You can set an exception handler on the socket that will be called if an exception occurs:

    def server = vertx.createNetServer()

    server.connectHandler { sock -> 
        sock.exceptionHandler { e -> println "Oops! $e" }
    }.listen(1234, "localhost")


### Read and Write Streams

NetSocket also implements `org.vertx.groovy.core.streams.ReadStream` and `org.vertx.groovy.core.streams.WriteStream`. This allows flow control to occur on the connection and the connection data to be pumped to and from other object such as HTTP requests and responses, WebSockets and asynchronous files.

This will be discussed in depth in the chapter on Streams and Pumps.

## Scaling TCP Servers

A verticle instance is strictly single threaded.

If you create a simple TCP server and deploy a single instance of it then all the handlers for that server are always executed on the same event loop (thread).

This means that if you are running on a server with a lot of cores, and you only have this one instance deployed then you will have at most one core utilised on your server! That's not very good, right?

To remedy this you can simply deploy more instances of the verticle in the server, e.g.

    vertx run MyScript.groovy -instances 20

The above would start 20 instances of MyScript.groovy to a locally running vert.x instance.

Once you do this you will find the echo server works functionally identically to before, but, *as if by magic*, all your cores on your server can be utilised and more work can be handled.

At this point you might be asking yourself *'Hold on, how can you have more than one server listening on the same host and port? Surely you will get port conflicts as soon as you try and deploy more than one instance?'*

*Vert.x does a little magic here*.

When you deploy another server on the same host and port as an existing server it doesn't actually try and create a new server listening on the same host/port.

Instead it internally maintains just a single server, and, as incoming connections arrive it distributes them in a round-robin fashion to any of the connect handlers set by the verticles.

Consequently vert.x TCP servers can scale over available cores while each vert.x verticle instance remains strictly single threaded, and you don't have to do any special tricks like writing load-balancers in order to scale your server on your multi-core machine.

## NetClient

A NetClient is used to make TCP connections to servers.

### Creating a Net Client

To create a TCP client you call the `createNetClient` method on your `vertx` instance.

    def client = vertx.createNetClient()

### Making a Connection

To actually connect to a server you invoke the `connect` method:

    def client = vertx.createNetClient()

    client.connect(1234, "localhost") { socket -> println "We have connected!" }

The connect method takes the port number as the first parameter, followed by the hostname or ip address of the server. The third parameter is a connect handler. This handler will be called when the connection actually occurs.

The argument passed into the connect handler is an instance of `org.vertx.groovy.core.net.NetSocket`, exactly the same as what is passed into the server side connect handler. Once given the `NetSocket` you can read and write data from the socket in exactly the same way as you do on the server side.

You can also close it, set the closed handler, set the exception handler and use it as a `ReadStream` or `WriteStream` exactly the same as the server side `NetSocket`.

### Catching exceptions on the Net Client

You can set an exception handler on the `NetClient`. This will catch any exceptions that occur during connection.

    def client = vertx.createNetClient()

    client.exceptionHandler { ex -> println "Failed to connect $ex" }

    client.connect(4242, "host-that-doesnt-exist") { socket -> puts "This won't get called" }


### Configuring Reconnection

A NetClient can be configured to automatically retry connecting or reconnecting to the server in the event that it cannot connect or has lost its connection. This is done by setting the properties `reconnectAttempts` and `reconnectInterval`:

    def client = vertx.createNetClient()

    client.reconnectAttempts = 1000

    client.reconnectInterval = 500

`reconnectAttempts` determines how many times the client will try to connect to the server before giving up. A value of `-1` represents an infinite number of times. The default value is `0` - i.e. no reconnection is attempted.

`reconnectInterval` detemines how long, in milliseconds, the client will wait between reconnect attempts. The default
 value is `1000`.

If an exception handler is set on the client, and reconnect attempts is not equal to `0`. Then the exception handler will not be called until the client gives up reconnecting.


### NetClient Properties

Just like `NetServer`, `NetClient` also has a set of TCP properties you can set which affect its behaviour. They have the same meaning as those on `NetServer`.

`NetClient` also has a further set of properties which are used to configure SSL. We'll discuss those later on.

## SSL Servers

Net servers can also be configured to work with [Transport Layer Security](http://en.wikipedia.org/wiki/Transport_Layer_Security) (previously known as SSL).

When a `NetServer` is working as an SSL Server the API of the `NetServer` and `NetSocket` is identical compared to when it working with standard sockets. Getting the server to use SSL is just a matter of configuring the `NetServer` before `listen` is called.

To enable SSL the property `SSL` to `true` on the Net Server.

The server must also be configured with a *key store* and an optional *trust store*.

These are both *Java keystores* which can be managed using the [keytool](http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/keytool.html) utility which ships with the JDK.

The keytool command allows you to create keystores, and import and export certificates from them.

The key store should contain the server certificate. This is mandatory - the client will not be able to connect to the server over SSL if the server does not have a certificate.

The key store is configured on the server using the `keyStorePath` and `keyStorePassword` properties.

The trust store is optional and contains the certificates of any clients it should trust. This is only used if client authentication is required.

To configure a server to use server certificates only:

    def server = vertx.createNetServer(SSL: true,
                                       keyStorePath: "/path/to/your/keystore/server-keystore.jks",
                                       keyStorePassword: "password")

Making sure that `server-keystore.jks` contains the server certificate.

To configure a server to also require client certificates:

    def server = vertx.createNetServer(SSL: true,
                                       keyStorePath: "/path/to/your/keystore/server-keystore.jks",
                                       keyStorePassword: "password"),
                                       trustStorePath: "/path/to/your/truststore/server-truststore.jks",
                                       trustStorePassword: "password",
                                       clientAuthRequired: true)

Making sure that `server-truststore.jks` contains the certificates of any clients who the server trusts.

If `clientAuthRequired` is set to `true` and the client cannot provide a certificate, or it provides a certificate that the server does not trust then the connection attempt will not succeed.

## SSL Clients

NetClients can also be easily configured to use SSL. They have the exact same API when using SSL as when using standard sockets.

To enable SSL on a `NetClient` the property `SSL` should be set to `true`.

If the property `trustAll` is set to `true`, then the client will trust all server certificates. The connection will still be encrypted but this mode is vulnerable to 'man in the middle' attacks - i.e. you can't be sure who you are connecting to. Use this with caution. Default value is `false`.

If `trustAll` is set to `false` (default) then a client trust store must be configured and should contain the certificates of the servers that the client trusts.

The client trust store is just a standard Java key store, the same as the key stores on the server side. The client trust store location is set with the property `trustStorePath`. If a server presents a certificate during connection which is not in the client trust store, the connection attempt will not succeed.

If the server requires client authentication then the client must present its own certificate to the server when connecting. This certificate should reside in the client key store. Again it's just a regular Java key store. The client keystore location is set with the property `keyStorePath`.

To configure a client to trust all server certificates (dangerous):

    def client = vertx.createNetClient(SSL: true, trustAll: true)

To configure a client to only trust those certificates it has in its trust store:

    def client = vertx.createNetClient(SSL: true,
                                       trustStorePath: "/path/to/your/client/truststore/client-truststore.jks"
                                       trustStorePassword: "password")

To configure a client to only trust those certificates it has in its trust store, and also to supply a client certificate:

    def client = vertx.createNetClient(SSL: true,
                                       trustStorePath: "/path/to/your/client/truststore/client-truststore.jks"
                                       trustStorePassword: "password",
                                       clientAuthRequired: true,
                                       keyStorePath: "/path/to/keystore/holding/client/cert/client-keystore.jks",
                                       keyStorePassword: "password")


# Flow Control - Streams and Pumps

There are several objects in vert.x that allow data to be read from and written to in the form of Buffers.

All operations in the vert.x API are non blocking; calls to write data return immediately and writes are internally queued.

It's not hard to see that if you write to an object faster than it can actually write the data to its underlying resource then the write queue could grow without bound - eventually resulting in exhausting available memory.

To solve this problem a simple flow control capability is provided by some objects in the vert.x API.

Any flow control aware object that can be written to implements `org.vertx.groovy.core.streams.ReadStream`, and any flow control object that can be read from implements `org.vertx.groovy.core.streams.WriteStream`.

Let's take an example where we want to read from a `ReadStream` and write the data to a `WriteStream`.

A very simple example would be reading from a `NetSocket` on a server and writing back to the same `NetSocket` - since `NetSocket` implements both `ReadStream` and `WriteStream`, but you can do this between any `ReadStream` and any `WriteStream`, including HTTP requests and response, async files, WebSockets, etc.

A naive way to do this would be to directly take the data that's been read and immediately write it to the NetSocket, for example:

    def server = vertx.createNetServer()

    server.connectHandler { sock ->
        sock.dataHandler { buffer ->
            // Write the data straight back
            sock.write(buffer)
        }
    }.listen(1234, "localhost")

There's a problem with the above example: If data is read from the socket faster than it can be written back to the socket, it will build up in the write queue of the AsyncFile, eventually running out of RAM. This might happen, for example if the client at the other end of the socket wasn't reading very fast, effectively putting back-pressure on the connection.

Since `NetSocket` implements `WriteStream`, we can check if the `WriteStream` is full before writing to it:


    def server = vertx.createNetServer()

    server.connectHandler { sock ->
        sock.dataHandler { buffer ->
            if (!sock.writeQueueFull) {
                sock.write(buffer)
            }
        }
    }.listen(1234, "localhost")

This example won't run out of RAM but we'll end up losing data if the write queue gets full. What we really want to do is pause the `NetSocket` when the write queue is full. Let's do that:

    def server = vertx.createNetServer()

    server.connectHandler { sock ->
        sock.dataHandler { buffer ->
            if (!sock.writeQueueFull) {
                sock.write(buffer)
            } else {
                sock.pause()
            }
        }
    }.listen(1234, "localhost")

We're almost there, but not quite. The `NetSocket` now gets paused when the file is full, but we also need to *unpause* it when the write queue has processed its backlog:

    def server = vertx.createNetServer()

    server.connectHandler { sock ->
        sock.dataHandler { buffer ->
            if (!sock.writeQueueFull) {
                sock.write(buffer)
            } else {
                sock.pause()
                sock.drainHandler { sock.resume() }
            }
        }
    }.listen(1234, "localhost")


And there we have it. The `drainHandler` event handler will get called when the write queue is ready to accept more data, this resumes the `NetSocket` which allows it to read more data.

It's very common to want to do this when writing vert.x applications, so we provide a helper class called `Pump` which does all this hard work for you. You just feed it the `ReadStream` and the `WriteStream` and it tell it to start:

    def server = vertx.createNetServer()

    server.connectHandler { sock -> Pump.create(sock, sock).start() }.listen(1234, "localhost")

Which does exactly the same thing as the more verbose example.

Let's look at the methods on `ReadStream` and `WriteStream` in more detail:

## ReadStream

`ReadStream` is implemented by `AsyncFile`, `HttpClientResponse`, `HttpServerRequest`, `WebSocket`, `NetSocket` and `SockJSSocket`.

methods:

* `dataHandler(handler)`: set a handler which will receive data from the `ReadStream`. As data arrives the handler will be passed a Buffer.
* `pause()`: pause the handler. When paused no data will be received in the `dataHandler`.
* `resume()`: resume the handler. The handler will be called if any data arrives.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `ReadStream`.
* `endHandler(handler)`: Will be called when end of stream is reached. This might be when EOF is reached if the `ReadStream` represents a file, or when end of request is reached if it's an HTTP request, or when the connection is closed if it's a TCP socket.

## WriteStream

`WriteStream` is implemented by `AsyncFile`, `HttpClientRequest`, `HttpServerResponse`, `WebSocket`, `NetSocket` and `SockJSSocket`

methods:

* `writeBuffer(buffer)`: write a Buffer to the `WriteStream`. This method will never block. Writes are queued internally and asynchronously written to the underlying resource.
* `setWriteQueueMaxSize(size)`: set the number of bytes at which the write queue is considered *full*, and the method `writeQueueFull()` returns `true`. Note that, even if the write queue is considered full, if `writeBuffer` is called the data will still be accepted and queued.
* `isWriteQueueFull()`: returns `true` if the write queue is considered full.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `WriteStream`.
* `drainHandler(handler)`: The handler will be called if the `WriteStream` is considered no longer full.

## Pump

Instances of `Pump` have the following methods:

* `start()`: Start the pump.
* `stop()`: Stops the pump. When the pump starts it is in stopped mode.
* `setWriteQueueMaxSize()`: This has the same meaning as `setWriteQueueMaxSize` on the `WriteStream`.
* `getBytesPumped()`: Returns total number of bytes pumped.

A pump can be started and stopped multiple times.

# Writing HTTP Servers and Clients

## Writing HTTP servers

Vert.x allows you to easily write full featured, highly performant and scalable HTTP servers.

### Creating an HTTP Server

To create an HTTP server you call the `createHttpServer` method on your `vertx` instance.

    def server = vertx.createHttpServer()

### Start the Server Listening

To tell that server to listen for incoming requests you use the `listen` method:

    def server = vertx.createHttpServer()

    server.listen(8080, "myhost")

The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If the hostname is omitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


### Getting Notified of Incoming Requests

To be notified when a request arrives you need to set a request handler. This is done by calling the `requestHandler` method of the server, passing in the handler:

    def server = vertx.createHttpServer()

    server.requestHandler{ request -> println "A request has arrived on the server!" }

    server.listen(8080, "localhost")

Every time a request arrives on the server the handler is called passing in an instance of `org.vertx.groovy.core.http
.HttpServerRequest`.

You can try it by running the verticle and pointing your browser at `http://localhost:8080`.

Similarly to `NetServer`, the return value of the `requestHandler` method is the server itself, so multiple invocations can be chained together. That means we can rewrite the above with:

    def server = vertx.createHttpServer()

    server.requestHandler{ request -> println "A request has arrived on the server!" }.listen(8080, "localhost")

or

    vertx.createHttpServer().requestHandler{ request ->
        println "A request has arrived on the server!"
    }.listen(8080, "localhost")


### Handling HTTP Requests

So far we have seen how to create an `HttpServer` and be notified of requests. Lets take a look at how to handle the requests and do something useful with them.

When a request arrives, the request handler is called passing in an instance of `HttpServerRequest`. This object represents the server side HTTP request.

The handler is called when the headers of the request have been fully read. If the request contains a body, that body may arrive at the server some time after the request handler has been called.

It contains methods to get the URI, path, request headers and request parameters. It also contains a `response` property which is a reference to an object that represents the server side HTTP response for the object.

#### Request Method

The request object has a property `method` which is a string representing what HTTP method was requested. Possible values for `method` are: `GET`, `PUT`, `POST`, `DELETE`, `HEAD`, `OPTIONS`, `CONNECT`, `TRACE`, `PATCH`.

#### Request URI

The request object has a property `uri` which contains the full URI (Uniform Resource Locator) of the request. For example, if the request URI was:

    /a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.uri` would contain the string `/a/b/c/page.html?param1=abc&param2=xyz`.

Request URIs can be relative or absolute (with a domain) depending on what the client sent. In many cases they will be relative.

The request uri contains the value as defined in [Section 5.1.2 of the HTTP specification - Request-URI](http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html)

#### Request Path

The request object has a property `path` which contains the path of the request. For example, if the request URI was:

    /a/b/c/page.html?param1=abc&param2=xyz

Then `request.path` would contain the string `/a/b/c/page.html`

#### Request Query

The request object has a property `query` which contains the query of the request. For example, if the request URI was:

    /a/b/c/page.html?param1=abc&param2=xyz

Then `request.query` would contain the string `param1=abc&param2=xyz`

#### Request Headers

A map of the request headers are available using the `headers` property on the request object.

Note that the header keys are always lower-cased before being put in the `headers` property.

Here's an example that echoes the headers to the output of the response. Run it and point your browser at `http://localhost:8080` to see the headers.

    def server = vertx.createHttpServer()

    server.requestHandler { request ->

        def sb = new StringBuffer()
        for (e in request.headers) {
            sb << e.key << ": " << e.value << '\n'
        }
        request.response.putHeader("Content-Type", "text/plain")
        request.response.end(sb.toString())
        }
    }.listen(8080, "localhost")


#### Request params

Similarly to the headers, the map of request parameters are available using the `params` property on the request
object.

Request parameters are sent on the request URI, after the path. For example if the URI was:

    /page.html?param1=abc&param2=xyz

Then the params map would contain the following entries:

    param1: 'abc'
    param2: 'xyz

#### Reading Data from the Request Body

Sometimes an HTTP request contains a request body that we want to read. As previously mentioned the request handler is called when only the headers of the request have arrived so the `HttpServerRequest` object does not contain the body. This is because the body may be very large and we don't want to create problems with exceeding available memory.

To receive the body, you set the `dataHandler` on the request object. This will then get called every time a chunk of the request body arrives. Here's an example:

    def server = vertx.createHttpServer()

    server.requestHandler{ request ->
        request.dataHandler { buffer -> println "I received ${buffer.length(}) bytes" }
    }.listen(8080, "localhost")

The `dataHandler` may be called more than once depending on the size of the body.

You'll notice this is very similar to how data from `NetSocket` is read.

The request object implements the `ReadStream` interface so you can pump the request body to a `WriteStream`. See the chapter on Streams and Pumps for a detailed explanation.

In many cases, you know the body is not large and you just want to receive it in one go. To do this you could do something like the following:

    def server = vertx.createHttpServer()

    server.requestHandler{ request ->

        def body = new Buffer(0)

        request.dataHandler { buffer -> body << buffer }

        request.endHandler { println "I received ${body.length(}) bytes" }

    }.listen(8080, "localhost")


Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the request.

If the HTTP request is using HTTP chunking, then each HTTP chunk of the request body will correspond to a single call of the data handler.

It's a very common use case to want to read the entire body before processing it, so vert.x allows a `bodyHandler` to be set on the request object.

The body handler is called only once when the *entire* request body has been read.

*Beware of doing this with very large requests since the entire request body will be stored in memory.*

Here's an example using `bodyHandler`:

    def server = vertx.createHttpServer()

    server.requestHandler{ request ->

        request.bodyHandler { body -> println "The total body received was ${body.length(}) bytes" }

    }.listen(8080, "localhost")

Simples, innit?

### HTTP Server Responses

As previously mentioned, the HTTP request object contains a property `response`. This is the HTTP response for the request. You use it to write the response back to the client.

### Setting Status Code and Message

To set the HTTP status code for the response use the `statusCode` property.

You can also use the `statusMessage` property to set the status message. If you do not set the status message a default message will be used.

    def server = vertx.createHttpServer()

    server.requestHandler{ request ->
        request.response.with {
            statusCode = 739
            statusMessage = "Too many gerbils"
            end()
        }
    }.listen(8080, "localhost")

The default value for `statusCode` is `200`.

#### Writing HTTP responses

To write data to an HTTP response, you invoke the `write` method or use the leftShift (<<) operator (They do the same thing). This method can be invoked multiple times before the response is ended. It can be invoked in a few ways:

With a single buffer:

    def myBuffer = ...
    request.response.write(myBuffer)
    
    // Or
    
    request.response << myBuffer

A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.response.write("hello")
    
    // Or
    
    request.response << "hello"

A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.

    request.response.write("hello", "UTF-16")

The `write` method is asynchronous and always returns immediately after the write has been queued.

The actual write might complete some time later. If you want to be informed when the actual write has completed you can pass in a handler as a final argument. This handler will then be invoked when the write has completed:

    request.response.write("hello") { println "It has actually been written" }

If you are just writing a single string or Buffer to the HTTP response you can write it and end the response in a single call to the `end` method.

The first call to `write` results in the response header being being written to the response.

Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the response, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry.

#### Ending HTTP responses

Once you have finished with the HTTP response you must call the `end()` method on it.

This method can be invoked in several ways:

With no arguments, the response is simply ended.

    request.response.end()

The method can also be called with a string or Buffer in the same way `write` is called. In this case it's just the same as calling write with a string or Buffer followed by calling `end` with no arguments. For example:

    request.response.end("That's all folks")

#### Closing the underlying connection

You can close the underlying TCP connection of the request by calling the `close` method.

    request.response.close()

#### Response headers

HTTP response headers can be added to the response by adding them to the `headers` property:

    request.response.headers["Cheese"] = "Stilton"
    request.response.headers["Hat colour"] = "Mauve"

Individual HTTP response headers can also be written using the `putHeader` method. This allows a fluent API since calls to `putHeader` can be chained:

    request.response.putHeader("Dream", "Elephants").putHeader("Invisible-Friend", "Bertie")

Response headers must all be added before any parts of the response body are written.

#### Chunked HTTP Responses and Trailers

Vert.x supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding). This allows the HTTP response body to be written in chunks, and is normally used when a large response body is being streamed to a client, whose size is not known in advance.

You put the HTTP response into chunked mode as follows:

    req.response.chunked = true

Default is non-chunked. When in chunked mode, each call to `response.write(...)` will result in a new HTTP chunk being written out.

When in chunked mode you can also write HTTP response trailers to the response. These are actually written in the final chunk of the response.

To add trailers to the response, add them to the `trailers` property:

    request.response.trailers["Rick Astley"] = "Never Gonna Give You Up"
    request.response.trailers["You"] = "RickRolled"

Like headers, individual HTTP response trailers can also be written using the `putTrailer` method. This allows a fluent API since calls to `putTrailer` can be chained:

    request.response.putTrailer("Cat-Food", "Whiskas").putTrailer("Eye-Wear", "Monocle")


### Serving files directly from disk

If you were writing a web server, one way to serve a file from disk would be to open it as an `AsyncFile` and pump it to the HTTP response. Or you could load it in one go using the file system API and write that to the HTTP response.

Alternatively, vert.x provides a method which allows you to send serve a file from disk to an HTTP response in one operation. Where supported by the underlying operating system this may result in the OS directly transferring bytes from the file to the socket without being copied through userspace at all.

Using `sendFile` is usually more efficient for large files, but may be slower than using `readFile` to manually read the file as a buffer and write it directly to the response.

To do this use the `sendFile` method on the HTTP response. Here's a simple HTTP web server that serves static files from the local `web` directory:

    vertx.createHttpServer().requestHandler { req ->
      def file = req.uri == "/" ? "index.html" : req.uri
      req.response.sendFile "web/$file"
    }.listen(8080, "localhost")

*Note: If you use `sendFile` while using HTTPS it will copy through userspace, since if the kernel is copying data directly from disk to socket it doesn't give us an opportunity to apply any encryption.*

**If you're going to write web servers using vert.x be careful that users cannot exploit the path (e.g. send a path with `/../` in it to access files outside the directory from which you want to serve them.**

### Pumping Responses

Since the HTTP Response implements `WriteStream` you can pump to it from any `ReadStream`, e.g. an `AsyncFile`, `NetSocket` or `HttpServerRequest`.

Here's an example which echoes HttpRequest headers and body back in the HttpResponse. It uses a pump for the body, so it will work even if the HTTP request body is much larger than can fit in memory at any one time:

    def server = vertx.createHttpServer()

    server.requestHandler{ req ->
        req.response.headers << req.headers
        Pump.createPump(req, req.response).start()
        req.endHandler { req.response.end() }
    }.listen(8080, "localhost")


## Writing HTTP Clients

### Creating an HTTP Client

To create an HTTP client you call the `createHttpClient` method on your `vertx` instance:

    def client = vertx.createHttpClient()

You set the port and hostname (or ip address) that the client will connect to using the `host` and `port` properties:

    HttpClient client = vertx.createHttpClient(port: 8181, host: "foo.com")

A single `HttpClient` always connects to the same host and port. If you want to connect to different servers, create more instances.

The default port is `80` and the default host is `localhost`. So if you don't explicitly set these values that's what the client will attempt to connect to.

### Pooling and Keep Alive

By default the `HttpClient` pools HTTP connections. As you make requests a connection is borrowed from the pool and returned when the HTTP response has ended.

If you do not want connections to be pooled you can call `setKeepAlive` with `false`:

    def client = vertx.createHttpClient(port: 8181, host: "foo.com", keepAlive: false)

In this case a new connection will be created for each HTTP request and closed once the response has ended.

You can set the maximum number of connections that the client will pool as follows:

    def client = vertx.createHttpClient(port: 8181, host: "foo.com", maxPoolSize: 10)

The default value is `1`.

### Closing the client

Any HTTP clients created in a verticle are automatically closed for you when the verticle is stopped, however if you want to close it explicitly you can:

    client.close()

### Making Requests

To make a request using the client you invoke one the methods named after the HTTP method that you want to invoke.

For example, to make a `POST` request:

    def client = vertx.createHttpClient(host: "foo.com")

    def request = client.post("/some-path/") { resp ->
      println "Got a response: ${resp.statusCode}"        
    }

    request.end()

To make a PUT request use the `put` method, to make a GET request use the `get` method, etc.

Legal request methods are: `get`, `put`, `post`, `delete`, `head`, `options`, `connect`, `trace` and `patch`.

The general modus operandi is you invoke the appropriate method passing in the request URI as the first parameter. The second parameter is an event handler which will get called when the corresponding response arrives. The response handler is passed the client response object as an argument.

The value specified in the request URI corresponds to the Request-URI as specified in [Section 5.1.2 of the HTTP specification](http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html). In most cases it will be a relative URI.

*Please note that the domain/port that the client connects to is determined by `setPort` and `setHost`, and is not parsed from the uri.*

The return value from the appropriate request method is an instance of `org.vertx.groovy.core.http.HTTPClientRequest`. You can use this to add headers to the request, and to write to the request body. The request object implements `WriteStream`.

Once you have finished with the request you must call the `end` method.

If you don't know the name of the request method in advance there is a general `request` method which takes the HTTP method as a parameter:

    def client = vertx.createHttpClient(host: "foo.com")

    def request = client.request("POST", "/some-path/") { resp ->
        println "Got a response: ${resp.statusCode}"
    }

    request.end()

There is also a method called `getNow` which does the same as `get`, but automatically ends the request. This is useful for simple GETs which don't have a request body:

    def client = vertx.createHttpClient(host: "foo.com")

    client.getNow("/some-path/") { resp ->
        println "Got a response: ${resp.statusCode}"
    }

With `getNow` there is no return value.

#### Writing to the request body

Writing to the client request body has a very similar API to writing to the server response body.

To write data to an `HttpClientRequest` object, you invoke the `write` method, or use the leftShift operator (they do the same thing). This method can be called multiple times before the request has ended. It can be invoked in a few ways:

With a single buffer:

    def myBuffer = ...
    request.write(myBuffer)
    
    // Or
    
    request << myBuffer

A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.write("hello")
    
    // Or
    
    request << "hello"

A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.

    request.write("hello", "UTF-16")

The `write` method is asynchronous and always returns immediately after the write has been queued. The actual write might complete some time later.

If you want to be informed when the actual write has completed you can pass in a closure as a final argument. This closure will be executed when the write has completed:

    request.response.write("hello") { println "It's actually been written" }

If you are just writing a single string or Buffer to the HTTP request you can write it and end the request in a single call to the `end` method.

The first call to `write` results in the request header being written to the request.

Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the request, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry.


#### Ending HTTP requests

Once you have finished with the HTTP request you must call the `end` method on it.

This method can be invoked in several ways:

With no arguments, the request is simply ended.

    request.end()

The method can also be called with a string or Buffer in the same way `write` is called. In this case it's just the same as calling write with a string or Buffer followed by calling `end` with no arguments.

#### Writing Request Headers

To write headers to the request, add them to `headers` property:

    def client = vertx.createHttpClient(host: "foo.com")

    def request = client.post("/some-path/") { resp ->
        println "Got a response: ${resp.statusCode}"
    }

    request.headers["Some-Header"] = "Some-Value"
    request.end()

You can also add them using the `putHeader` method. This enables a more fluent API since calls can be chained, for example:

    request.putHeader("Some-Header", "Some-Value").putHeader("Some-Other", "Blah")

These can all be chained together as per the common vert.x API pattern:

    vertx.createHttpClient(host: "foo.com").post("/some-path/") { resp ->
        println "Got a response: ${resp.statusCode}"
    }.putHeader("Some-Header", "Some-Value").end()

#### HTTP chunked requests

Vert.x supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding) for requests. This allows the HTTP request body to be written in chunks, and is normally used when a large request body is being streamed to the server, whose size is not known in advance.

You put the HTTP request into chunked mode as follows:

    request.chunked = true

Default is non-chunked. When in chunked mode, each call to `request.write(...)` will result in a new HTTP chunk being written out.

### HTTP Client Responses

Client responses are received as an argument to the response handler that is passed into one of the request methods on the HTTP client.

The response object implements `ReadStream`, so it can be pumped to a `WriteStream` like any other `ReadStream`.

To query the status code of the response use the `statusCode` property. The `statusMessage` property contains the status message. For example:

    def client = vertx.createHttpClient(host: "foo.com")

    client.getNow("/some-path/") { resp ->
        println "server returned status code: ${resp.statusCode}"
        println "server returned status message: ${resp.statusMessage}"
    }    


#### Reading Data from the Response Body

The API for reading a http client response body is very similar to the API for reading a http server request body.

Sometimes an HTTP response contains a body that we want to read. Like an HTTP request, the client response handler is called when all the response headers have arrived, not when the entire response body has arrived.

To receive the response body, you set a `dataHandler` on the response object which gets called as parts of the HTTP response arrive. Here's an example:

    def client = vertx.createHttpClient(host: "foo.com")

    client.getNow("/some-path/") { resp ->
        resp.dataHandler { buffer ->
            println "I received ${buffer.length} bytes"
        }
    }


The response object implements the `ReadStream` interface so you can pump the response body to a `WriteStream`. See the chapter on streams and pump for a detailed explanation.

The `dataHandler` can be called multiple times for a single HTTP response.

As with a server request, if you wanted to read the entire response body before doing something with it you could do something like the following:

    def client = vertx.createHttpClient(host: "foo.com")

    client.getNow("/some-path/") { resp ->
        def body = new Buffer()
        resp.dataHandler { buffer ->
            body << buffer
        }
        resp.endHandler { println "The total body received was ${body.length} bytes" }
    }

Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the response.

If the HTTP response is using HTTP chunking, then each chunk of the response body will correspond to a single call to the `dataHandler`.

It's a very common use case to want to read the entire body in one go, so vert.x allows a `bodyHandler` to be set on the response object.

The body handler is called only once when the *entire* response body has been read.

*Beware of doing this with very large responses since the entire response body will be stored in memory.*

Here's an example using `bodyHandler`:

    def client = vertx.createHttpClient(host: "foo.com")

    client.getNow("/some-path/") { resp ->
        resp.bodyHandler { body ->
            println "The total body received was ${body.length} bytes"
        }
    }

## Pumping Requests and Responses

The HTTP client and server requests and responses all implement either `ReadStream` or `WriteStream`. This means you can pump between them and any other read and write streams.

### 100-Continue Handling

According to the [HTTP 1.1 specification](http://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html) a client can set a header `Expect: 100-Continue` and send the request header before sending the rest of the request body.

The server can then respond with an interim response status `Status: 100 (Continue)` to signify the client is ok to send the rest of the body.

The idea here is it allows the server to authorise and accept/reject the request before large amounts of data is sent. Sending large amounts of data if the request might not be accepted is a waste of bandwidth and ties up the server in reading data that it will just discard.

Vert.x allows you to set a `continueHandler` on the client request object. This will be called if the server sends back a `Status: 100 (Continue)` response to signify it is ok to send the rest of the request.

This is used in conjunction with the `sendHead` method to send the head of the request.

An example will illustrate this:

    def client = vertx.createHttpClient(host: "foo.com")

    def request = client.put("/some-path/") { resp ->
        println "Got a response ${resp.statusCode}"
    }

    request.headers["Expect"] = "100-Continue"

    request.continueHandler {
        // OK to send rest of body
        request << "Some data"
        request.end()
    }

    request.sendHead()

## HTTPS Servers

HTTPS servers are very easy to write using vert.x.

An HTTPS server has an identical API to a standard HTTP server. Getting the server to use HTTPS is just a matter of configuring the HTTP Server before `listen` is called.

Configuration of an HTTPS server is done in exactly the same way as configuring a `NetServer` for SSL. Please see SSL server chapter for detailed instructions.

## HTTPS Clients

HTTPS clients can also be very easily written with vert.x

Configuring an HTTP client for HTTPS is done in exactly the same way as configuring a `NetClient` for SSL. Please see SSL client chapter for detailed instructions.

## Scaling HTTP servers

Scaling an HTTP or HTTPS server over multiple cores is as simple as deploying more instances of the verticle. For example:

    vertx deploy foo.MyServer -instances 20

The scaling works in the same way as scaling a `NetServer`. Please see the chapter on scaling Net Servers for a detailed explanation of how this works.

# Routing HTTP requests with Pattern Matching

Vert.x lets you route HTTP requests to different handlers based on pattern matching on the request path. It also enables you to extract values from the path and use them as parameters in the request.

This is particularly useful when developing REST-style web applications.

To do this you simply create an instance of `org.vertx.java.core.http.RouteMatcher` and use it as handler in an HTTP server. See the chapter on HTTP servers for more information on setting HTTP handlers. Here's an example:

    def server = vertx.createHttpServer()

    def routeMatcher = new RouteMatcher()

    server.requestHandler(routeMatcher.asClosure()).listen(8080, "localhost")

## Specifying matches

You can then add different matches to the route matcher. For example, to send all GET requests with path `/animals/dogs` to one handler and all GET requests with path `/animals/cats` to another handler you would do:

    def server = vertx.createHttpServer()

    def routeMatcher = new RouteMatcher()

    routeMatcher.get("/animals/dogs") { req ->
        req.response.end "You requested dogs"
    }
   
    routeMatcher.get("/animals/cats") { req ->
        req.response.end "You requested cats"
    }
   
    server.requestHandler(routeMatcher.asClosure()).listen(8080, "localhost")

Corresponding methods exist for each HTTP method - `get`, `post`, `put`, `delete`, `head`, `options`, `trace`, `connect` and `patch`.

There's also an `all` method which applies the match to any HTTP request method.

The handler specified to the method is just a normal HTTP server request handler, the same as you would supply to the `requestHandler` method of the HTTP server.

You can provide as many matches as you like and they are evaluated in the order you added them, the first matching one will receive the request.

A request is sent to at most one handler.

## Extracting parameters from the path

If you want to extract parameters from the path, you can do this too, by using the `:` (colon) character to denote the name of a parameter. For example:

    def server = vertx.createHttpServer()

    def routeMatcher = new RouteMatcher()

    routeMatcher.put("/:blogname/:post") { req ->
        String blogName = req.params["blogname"]
        String post = req.params["post"]
        req.response.end "blogname is ${blogName}, post is $post"
    }

    server.requestHandler(routeMatcher).listen(8080, "localhost")

Any params extracted by pattern matching are added to the map of request parameters.

In the above example, a PUT request to `/myblog/post1` would result in the variable `blogName` getting the value `myblog` and the variable `post` getting the value `post1`.

Valid parameter names must start with a letter of the alphabet and be followed by any letters of the alphabet or digits.

## Extracting params using Regular Expressions

Regular Expressions can be used to extract more complex matches. In this case capture groups are used to capture any parameters.

Since the capture groups are not named they are added to the request with names `param0`, `param1`, `param2`, etc.

Corresponding methods exist for each HTTP method - `getWithRegEx`, `postWithRegEx`, `putWithRegEx`, `deleteWithRegEx`, `headWithRegEx`, `optionsWithRegEx`, `traceWithRegEx`, `connectWithRegEx` and `patchWithRegEx`.

There's also an `allWithRegEx` method which applies the match to any HTTP request method.

For example:

    def server = vertx.createHttpServer()

    def routeMatcher = new RouteMatcher()

    routeMatcher.allWithRegEx("\\/([^\\/]+)\\/([^\\/]+)") { req ->
        def first = req.params["param0"]
        def second = req.params["param1"]
        req.response.end "first is $first and second is $second"
    }

    server.requestHandler(routeMatcher).listen(8080, "localhost")

Run the above and point your browser at `http://localhost:8080/animals/cats`.

## Handling requests where nothing matches

You can use the `noMatch` method to specify a handler that will be called if nothing matches. If you don't specify a no match handler and nothing matches, a 404 will be returned.

    routeMatcher.noMatch{ req ->
        req.response.end "Nothing matched"
    }

# WebSockets

[WebSockets](http://en.wikipedia.org/wiki/WebSocket) are a feature of HTML 5 that allows a full duplex socket-like connection between HTTP servers and HTTP clients (typically browsers).

## WebSockets on the server

To use WebSockets on the server you create an HTTP server as normal, but instead of setting a `requestHandler` you set a `websocketHandler` on the server.

    def server = vertx.createHttpServer()

    server.websocketHandler{ ws ->
        println "A websocket has connected!"
    }.listen(8080, "localhost")


### Reading from and Writing to WebSockets

The `websocket` instance passed into the handler implements both `ReadStream` and `WriteStream`, so you can read and write data to it in the normal ways - i.e by setting a `dataHandler` and calling the `writeBuffer` method.

You can also use the leftShift (<<) operator to write data to the `websocket`.

See the chapter on `NetSocket` and streams and pumps for more information.

For example, to echo all data received on a WebSocket:

    def server = vertx.createHttpServer()

    server.websocketHandler{ ws ->
        Pump.createPump(ws, ws).start()
    }.listen(8080, "localhost")

The `websocket` instance also has method `writeBinaryFrame` for writing binary data. This has the same effect as calling `writeBuffer`.

Another method `writeTextFrame` also exists for writing text data. This is equivalent to calling

    websocket.writeBuffer(new Buffer("some-string"))

### Rejecting WebSockets

Sometimes you may only want to accept WebSockets which connect at a specific path.

To check the path, you can query the `path` property of the `websocket`. You can then call the `reject` method to reject the `websocket`.

    def server = vertx.createHttpServer()

    server.websocketHandler{ ws ->
        if (ws.path == "/services/echo") {
            Pump.createPump(ws, ws).start()
        } else {
            ws.reject()
        }        
    }.listen(8080, "localhost")


## WebSockets on the HTTP client

To use WebSockets from the HTTP client, you create the HTTP client as normal, then call the `connectWebsocket` method, passing in the URI that you wish to connect to at the server, and a handler.

The handler will then get called if the WebSocket successfully connects. If the WebSocket does not connect - perhaps the server rejects it - then any exception handler on the HTTP client will be called.

Here's an example of WebSocket connection;

    def client = vertx.createHttpClient()

    client.connectWebsocket("http://localhost:8080/some-uri") { ws ->
        // Connected!
    }

Again, the client side WebSocket implements `ReadStream` and `WriteStream`, so you can read and write to it in the same way as any other stream object.

## WebSockets in the browser

To use WebSockets from a compliant browser, you use the standard WebSocket API. Here's some example client side JavaScript which uses a WebSocket.

    <script>

        var socket = new WebSocket("ws://localhost:8080/services/echo");

        socket.onmessage = function(event) {
            alert("Received data from websocket: " + event.data);
        }

        socket.onopen = function(event) {
            alert("Web Socket opened");
            socket.send("Hello World");
        };

        socket.onclose = function(event) {
            alert("Web Socket closed");
        };

    </script>

For more information see the [WebSocket API documentation](http://dev.w3.org/html5/websockets/)

## Routing WebSockets with Pattern Matching

**TODO**

# SockJS

WebSockets are a new technology, and many users are still using browsers that do not support them, or which support older, pre-final, versions.

Moreover, WebSockets do not work well with many corporate proxies. This means that's it's not possible to guarantee a WebSocket connection is going to succeed for every user.

Enter SockJS.

SockJS is a client side JavaScript library and protocol which provides a simple WebSocket-like interface to the client side JavaScript developer irrespective of whether the actual browser or network will allow real WebSockets.

It does this by supporting various different transports between browser and server, and choosing one at runtime according to browser and network capabilities. All this is transparent to you - you are simply presented with the WebSocket-like interface which *just works*.

Please see the [SockJS website](https://github.com/sockjs/sockjs-client) for more information.

## SockJS Server

Vert.x provides a complete server side SockJS implementation.

This enables vert.x to be used for modern, so-called *real-time* (this is the *modern* meaning of *real-time*, not to be confused by the more formal pre-existing definitions of soft and hard real-time systems) web applications that push data to and from rich client-side JavaScript applications, without having to worry about the details of the transport.

To create a SockJS server you simply create a HTTP server as normal and then call the `createSockJSServer` method of your `vertx` instance passing in the Http server:

    def httpServer = vertx.createHttpServer()

    def sockJSServer = vertx.createSockJSServer(httpServer)

Each SockJS server can host multiple *applications*.

Each application is defined by some configuration, and provides a handler which gets called when incoming SockJS connections arrive at the server.

For example, to create a SockJS echo application:

    def httpServer = vertx.createHttpServer()

    def sockJSServer = vertx.createSockJSServer(httpServer)

    def config = ["prefix: "/echo"]

    sockJSServer.installApp(config) { sock ->
        Pump.createPump(sock, sock).start()
    }

    httpServer.listen(8080)

The configuration is an instance of map, and can contain the following settings:

* `prefix`: A url prefix for the application. All http requests whose paths begins with selected prefix will be handled by the application. This property is mandatory.
* `insert_JSESSIONID`: Some hosting providers enable sticky sessions only to requests that have JSESSIONID cookie set. This setting controls if the server should set this cookie to a dummy value. By default setting JSESSIONID cookie is enabled. More sophisticated beaviour can be achieved by supplying a function.
* `session_timeout`: The server sends a `close` event when a client receiving connection have not been seen for a while. This delay is configured by this setting. By default the `close` event will be emitted when a receiving connection wasn't seen for 5 seconds.
* `heartbeat_period`: In order to keep proxies and load balancers from closing long running http requests we need to pretend that the connection is active and send a heartbeat packet once in a while. This setting controls how often this is done. By default a heartbeat packet is sent every 25 seconds.
* `max_bytes_streaming`: Most streaming transports save responses on the client side and don't free memory used by delivered messages. Such transports need to be garbage-collected once in a while. `max_bytes_streaming` sets a minimum number of bytes that can be send over a single http streaming request before it will be closed. After that client needs to open new request. Setting this value to one effectively disables streaming and will make streaming transports to behave like polling transports. The default value is 128K.
* `library_url`: Transports which don't support cross-domain communication natively ('eventsource' to name one) use an iframe trick. A simple page is served from the SockJS server (using its foreign domain) and is placed in an invisible iframe. Code run from this iframe doesn't need to worry about cross-domain issues, as it's being run from domain local to the SockJS server. This iframe also does need to load SockJS javascript client library, and this option lets you specify its url (if you're unsure, point it to the latest minified SockJS client release, this is the default). The default value is `http://cdn.sockjs.org/sockjs-0.1.min.js`

## Reading and writing data from a SockJS server

The object passed into the SockJS handler implements `ReadStream` and `WriteStream` much like `NetSocket` or `WebSocket`. You can therefore use the standard API for reading and writing to the SockJS socket or using it in pumps.

See the chapter on Streams and Pumps for more information.

## SockJS client

For full information on using the SockJS client library please see the SockJS website. A simple example:

    <script>
       var sock = new SockJS('http://mydomain.com/my_prefix');

       sock.onopen = function() {
           console.log('open');
       };

       sock.onmessage = function(e) {
           console.log('message', e.data);
       };

       sock.onclose = function() {
           console.log('close');
       };
    </script>

As you can see the API is very similar to the WebSockets API.

# SockJS - EventBus Bridge

## Setting up the Bridge

By connecting up SockJS and the vert.x event bus we create a distributed event bus which not only spans multiple vert.x instances on the server side, but can also include client side JavaScript running in browsers.

We can therefore create a huge distributed bus encompassing many browsers and servers. The browsers don't have to be connected to the same server as long as the servers are connected.

On the server side we have already discussed the event bus API.

We also provide a client side JavaScript library called `vertxbus.js` which provides the same event bus API, but on the client side.

This library internally uses SockJS to send and receive data to a SockJS vert.x server called the SockJS bridge. It's the bridge's responsibility to bridge data between SockJS sockets and the event bus on the server side.

Creating a Sock JS bridge is simple. You just call the `bridge` method on the SockJS server.

You will also need to secure the bridge (see below).

The following example bridges the event bus to client side JavaScript:

    def server = vertx.createHttpServer()

    def config = ["prefix": "/eventbus"]

    vertx.createSockJSServer(server).bridge(config, [], [])

    server.listen(8080)

The SockJS bridge currently only works with JSON event bus messages.

## Using the Event Bus from client side JavaScript

Once you've set up a bridge, you can use the event bus from the client side as follows:

In your web page, you need to load the script `vertxbus.js`, then you can access the vert.x event bus API. Here's a rough idea of how to use it. For a full working examples, please consult the bundled examples.

    <script src="http://cdn.sockjs.org/sockjs-0.2.1.min.js"></script>
    <script src='vertxbus.js'></script>

    <script>

        var eb = new vertx.EventBus('http://localhost:8080/eventbus');
        
        eb.onopen = function() {
        
          eb.registerHandler('some-address', function(message) {

            console.log('received a message: ' + JSON.stringify(message);

          });

          eb.send('some-address', {name: 'tim', age: 587});
        
        }
       
    </script>

You can find `vertxbus.js` in the `client` directory of the vert.x distribution.

The first thing the example does is to create a instance of the event bus

    var eb = new vertx.EventBus('http://localhost:8080/eventbus');

The parameter to the constructor is the URI where to connect to the event bus. Since we create our bridge with the prefix `eventbus` we will connect there.

You can't actually do anything with the bridge until it is opened. When it is open the `onopen` handler will be called.

The client side event bus API for registering and unregistering handlers and for sending messages is exactly the same as the server side one. Please consult the chapter on the event bus for full information.

**There is one more thing to do before getting this working, please read the following section....**

## Securing the Bridge

If you started a bridge like in the above example without securing it, and attempted to send messages through it you'd find that the messages mysteriously disappeared. What happened to them?

For most applications you probably don't want client side JavaScript being able to send just any message to any verticle on the server side or to all other browsers.

For example, you may have a persistor verticle on the event bus which allows data to be accessed or deleted. We don't want badly behaved or malicious clients being able to delete all the data in your database! Also, we don't necessarily want any client to be able to listen in on any topic.

To deal with this, a SockJS bridge will, by default refuse to let through any messages. It's up to you to tell the bridge what messages are ok for it to pass through. (There is an exception for reply messages which are always allowed through).

In other words the bridge acts like a kind of firewall which has a default *deny-all* policy.

Configuring the bridge to tell it what messages it should pass through is easy. You pass in two lists of JSON objects (represented as maps) that represent *matches*, as the final argument in the call to `bridge`.

The first list is the *inbound* list and represents the messages that you want to allow through from the client to the server. The second list is the *outbound* list and represents the messages that you want to allow through from the server to the client.

Each match can have up to three fields:

1. `address`: This represents the exact address the message is being sent to. If you want to filter messages based on an exact address you use this field.
2. `address_re`: This is a regular expression that will be matched against the address. If you want to filter messages based on a regular expression you use this field. If the `address` field is specified this field will be ignored.
3. `match`: This allows you to filter messages based on their structure. Any fields in the match must exist in the message with the same values for them to be passed. This currently only works with JSON messages.

When a message arrives at the bridge, it will look through the available permitted entries.

* If an `address` field has been specified then the `address` must match exactly with the address of the message for it to be considered matched.

* If an `address` field has not been specified and an `address_re` field has been specified then the regular expression in `address_re` must match with the address of the message for it to be considered matched.

* If a `match` field has been specified, then also the structure of the message must match.

Here is an example:

    def server = vertx.createHttpServer()

    def config = ["prefix": "/eventbus"]

    // This defines the matches for client --> server traffic
    def inboundPermitted = []

    // Let through any messages sent to 'demo.orderMgr'
    inboundPermitted << ["address": "demo.orderMgr"]

    // Allow messages to the address 'demo.persistor' as long as the messages
    // have an action field with value 'find' and a collection field with value
    // 'albums'
    inboundPermitted << ["address": "demo.persistor",
                  "match": [ "action": "find",
                             "collection": "albums"]]

    // Allow through any message with a field `wibble` with value `foo`.
    inboundPermitted << ["match", ["wibble": "foo"]]

    // This defines the matches for server --> client traffic
    def outboundPermitted = []

    // Let through any messages from address "ticker.mystock"
    outboundPermitted << ["address": "ticker.mystock"]

    // Let through any messages from addresses starting with "news." (e.g. news.europe, news.usa, etc)
    outboundPermitted << ["address_re": "news\\..+"]

    vertx.createSockJSBridge(server).bridge(config, inboundPermitted, outboundPermitted)

    server.listen(8080)


To let all messages through you can specify a list with a single empty map which will match all messages.

    ...

    vertx.createSockJSBridge(server).bridge(config, [[:]], [[:]])

    ...

**Be very careful!**

## Messages that require authorisation

The bridge can also refuse to let certain messages through if the user is not authorised.

To enable this you need to make sure an instance of the `vertx.auth-mgr` module is available on the event bus. (Please see the modules manual for a full description of modules).

To tell the bridge that certain messages require authorisation before being passed, you add the field `requires_auth` with the value of `true` in the match. The default value is `false`. For example, the following match:

    permitted << ["address": "demo.persistor",
                  "match": [ "action": "save",
                             "collection": "orders"],
                  "requires_auth": true]
    
This tells the bridge that any messages to save orders in the `orders` collection, will only be passed if the user is successful authenticated (i.e. logged in ok) first.    
    
When a message is sent from the client that requires authorisation, the client must pass a field `sessionID` with the message that contains the unique session ID that they obtained when they logged in with the `auth-mgr`.

When the bridge receives such a message, it will send a message to the `auth-mgr` to see if the session is authorised for that message. If the session is authorised the bridge will cache the authorisation for a certain amount of time (five minutes by default)

# File System

Vert.x lets you manipulate files on the file system. File system operations are asynchronous and take a handler method as the last argument. This method will be called when the operation is complete, or an error has occurred.

The argument passed into the handler is an instance of `org.vertx.java.core.AsyncResult`. Instances of this class have two fields: `exception` - If the operation has failed this will be set; `result` - If the operation has succeeded this will contain the result.

## Synchronous forms

For convenience, we also provide synchronous forms of most operations. It's highly recommended the asynchronous forms are always used for real applications.

The synchronous form does not take a handler as an argument and returns its results directly. The name of the synchronous method is the same as the name as the asynchronous form with `Sync` appended.

## copy

Copies a file.

This method can be called in two different ways:

* `copy(source, destination, handler)`

Non recursive file copy. `source` is the source file name. `destination` is the destination file name.

Here's an example:

    vertx.fileSystem.copy("foo.dat", "bar.dat") { ar ->
        if (ar.succeeded() {
            log.info("Copy was successful")
        } else {
            log.error("Failed to copy", ar.exception)
        }
    }

* `copy(source, destination, recursive, handler)`

Recursive copy. `source` is the source file name. `destination` is the destination file name. `recursive` is a boolean flag - if `true` and source is a directory, then a recursive copy of the directory and all its contents will be attempted.

## move

Moves a file.

`move(source, destination, handler)`

`source` is the source file name. `destination` is the destination file name.

## truncate

Truncates a file.

`truncate(file, len, handler)`

`file` is the file name of the file to truncate. `len` is the length in bytes to truncate it to.

## chmod

Changes permissions on a file or directory.

This method can be called in two different ways:

* `chmod(file, perms, handler)`.

Change permissions on a file.

`file` is the file name. `perms` is a Unix style permissions string made up of 9 characters. The first three are the owner's permissions. The second three are the group's permissions and the third three are others permissions. In each group of three if the first character is `r` then it represents a read permission. If the second character is `w`  it represents write permission. If the third character is `x` it represents execute permission. If the entity does not have the permission the letter is replaced with `-`. Some examples:

    rwxr-xr-x
    r--r--r--

* `chmod(file, perms, dirPerms, handler)`.

Recursively change permissionson a directory. `file` is the directory name. `perms` is a Unix style permissions to apply recursively to any files in the directory. `dirPerms` is a Unix style permissions string to apply to the directory and any other child directories recursively.

## props

Retrieve properties of a file.

`props(file, handler)`

`file` is the file name. The props are returned in the handler. The results is an object with the following properties:

* `creationTime`. Time of file creation.
* `lastAccessTime`. Time of last file access.
* `lastModifiedTime`. Time file was last modified.
* `isDirectory`. This will have the value `true` if the file is a directory.
* `isRegularFile`. This will have the value `true` if the file is a regular file (not symlink or directory).
* `isSymbolicLink`. This will have the value `true` if the file is a symbolic link.
* `isOther`. This will have the value `true` if the file is another type.

Here's an example:

    vertx.fileSystem("foo.dat", "bar.dat") { ar ->
        if (ar.succeeeded()) {        
            println "Props are: Last accessed: ${ar.result.lastAccessTime}"
            // etc
        } else {
            log.error("Failed to get props", ar.exception)
        }
    }

## lprops

Retrieve properties of a link. This is like `props` but should be used when you want to retrieve properties of a link itself without following it.

It takes the same arguments and provides the same results as `props`.

## link

Create a hard link.

`link(link, existing, handler)`

`link` is the name of the link. `existing` is the exsting file (i.e. where to point the link at).

## symlink

Create a symbolic link.

`symlink(link, existing, handler)`

`link` is the name of the symlink. `existing` is the exsting file (i.e. where to point the symlink at).

## unlink

Unlink (delete) a link.

`unlink(link, handler)`

`link` is the name of the link to unlink.

## readSymLink

Reads a symbolic link - i.e returns the path representing the file that the symbolic link specified by `link` points to.

`readSymLink(link, handler)`

`link` is the name of the link to read. An usage example would be:

    vertx.fileSystem.readSymLink("somelink") { ar ->
        if (ar.succeeded()) {
            println "Link points at ${ar.result}"
        } else {
            log.error("Failed to read", ar.exception)
        }
    }

## delete

Deletes a file or recursively deletes a directory.

This method can be called in two ways:

* `delete(file, handler)`

Deletes a file. `file` is the file name.

* `delete(file, recursive, handler)`

If `recursive` is `true`, it deletes a directory with name `file`, recursively. Otherwise it just deletes a file.

## mkdir

Creates a directory.

This method can be called in three ways:

* `mkdir(dirname, handler)`

Makes a new empty directory with name `dirname`, and default permissions `

* `mkdir(dirname, createParents, handler)`

If `createParents` is `true`, this creates a new directory and creates any of its parents too. Here's an example

    vertx.fileSystem.mkdir("a/b/c", true) { ar->
        if (ar.succeeded()) {
            println "Directory created ok"
        } else {
            log.error("Failed to mkdir", ar.exception)
        }
    }

* `mkdir(dirname, createParents, perms, handler)`

Like `mkdir(dirname, createParents, handler)`, but also allows permissions for the newly created director(ies) to be specified. `perms` is a Unix style permissions string as explained earlier.

## readDir

Reads a directory. I.e. lists the contents of the directory.

This method can be called in two ways:

* `readDir(dirName)`

Lists the contents of a directory

* `readDir(dirName, filter)`

List only the contents of a directory which match the filter. Here's an example which only lists files with an extension `txt` in a directory.

    vertx.fileSystem.readDir("mydirectory", ".*\\.txt") { ar ->
        if (ar.succeeded()) {
            println "Directory contains these .txt files"
            for (file in ar.result) {
                println file
            }
        } else {
            log.error("Failed to read", ar.exception)
        }
    }

The filter is a regular expression.

## readFile

Read the entire contents of a file in one go. *Be careful if using this with large files since the entire file will be stored in memory at once*.

`readFile(file)`. Where `file` is the file name of the file to read.

The body of the file will be returned as an instance of `org.vertx.java.core.buffer.Buffer` in the handler.

Here is an example:

    vertx.fileSystem.readFile("myfile.dat") { ar ->
        if (ar.succeeded()) {
            println "File contains: ${ar.result.length} bytes"
        } else {
            log.error("Failed to read", ar.exception)
        }
    }

## writeFile

Writes an entire `Buffer` or a string into a new file on disk.

`writeFile(file, data, handler)` Where `file` is the file name. `data` is a `Buffer` or string.

## createFile

Creates a new empty file.

`createFile(file, handler)`. Where `file` is the file name.

## exists

Checks if a file exists.

`exists(file, handler)`. Where `file` is the file name.

The result is returned in the handler.

    vertx.fileSystem.exists("some-file.txt") { ar ->
        if (ar.succeeded()) {
            println "File exists? ${ar.result}"
        } else {
            log.error("Failed to check existence", ar.exception)
        }
    }

## fsProps

Get properties for the file system.

`fsProps(file, handler)`. Where `file` is any file on the file system.

The result is returned in the handler. The result object is an instance of `org.vertx.java.core.file.FileSystemProps` has the following fields:

* `totalSpace`: Total space on the file system in bytes.
* `unallocatedSpace`: Unallocated space on the file system in bytes.
* `usableSpace`: Usable space on the file system in bytes.

Here is an example:

    vertx.fileSystem.fsProps("mydir") { ar ->
        if (ar.succeded() {
            println "total space: ${ar.result.totalSpace}"
            // etc
        } else {
            log.error("Failed to check existence", ar.exception)
        }
    }

## open

Opens an asynchronous file for reading \ writing.

This method can be called in four different ways:

* `open(file, handler)`

Opens a file for reading and writing. `file` is the file name. It creates it if it does not already exist.

* `open(file, perms, handler)`

Opens a file for reading and writing. `file` is the file name. It creates it if it does not already exist and assigns it the permissions as specified by `perms`.

* `open(file, perms, createNew, handler)`

Opens a file for reading and writing. `file` is the file name. It `createNew` is `true` it creates it if it does not already exist.

* `open(file, perms, read, write, createNew, handler)`

Opens a file. `file` is the file name. If `read` is `true` it is opened for reading. If `write` is `true` it is opened for writing. It `createNew` is `true` it creates it if it does not already exist.

* `open(file, perms, read, write, createNew, flush, handler)`

Opens a file. `file` is the file name. If `read` is `true` it is opened for reading. If `write` is `true` it is opened for writing. It `createNew` is `true` it creates it if it does not already exist. If `flush` is `true` all writes are immediately flushed through the OS cache (default value of `flush` is false).


When the file is opened, an instance of `org.vertx.java.core.file.AsyncFile` is passed into the result handler:

    vertx.fileSystem.open("some-file.dat") { ar ->
        if (ar.succeeded()) {
            println "File opened ok!"
            // etc
        } else {
            log.error("Failed to open file", ar.exception)
        }
    }

## AsyncFile

Instances of `org.vertx.groovy.core.file.AsyncFile` are returned from calls to `open` and you use them to read from and write to files asynchronously. They allow asynchronous random file access.

`AsyncFile` can provide instances of `ReadStream` and `WriteStream` via the `getReadStream` and `getWriteStream` methods, so you can pump files to and from other stream objects such as net sockets, HTTP requests and responses, and WebSockets.

They also allow you to read and write directly to them.

### Random access writes

To use an `AsyncFile` for random access writing you use the `write` method.

`write(buffer, position, handler)`.

The parameters to the method are: 

* `buffer`: the buffer to write.
* `position`: an integer position in the file where to write the buffer. If the position is greater or equal to the size of the file, the file will be enlarged to accomodate the offset.
* 'handler': a closure to call when the operation is complete.

Here is an example of random access writes:

    vertx.fileSystem.open("some-file.dat") { ar ->
        if (ar.succeeded()) {
            def asyncFile = ar.result
            // File open, write a buffer 5 times into a file
            def buff = new Buffer("foo")
            5.times {
                asyncFile.write(buff, buff.length * it) { ar2 ->
                    if (ar2.succeded()) {
                        println "Written ok!"
                        // etc
                    } else {
                        log.error("Failed to write", ar2.exception)
                    }
                }
            }
        } else {
            log.error("Failed to open file", ar.exception)
        }
    }

### Random access reads

To use an `AsyncFile` for random access reads you use the `read` method.

`read(buffer, offset, position, length, handler)`

The parameters to the method are: 

* `buffer`: the buffer into which the data will be read.
* `offset`: an integer offset into the buffer where the read data will be placed.
* `position`: the position in the file where to read data from.
* `length`: the number of bytes of data to read.
* 'handler': a closure to call when the operation is complete.


Here's an example of random access reads:

    vertx.fileSystem.open("some-file.dat") { ar ->
        if (ar.succeeded()) {
            def asyncFile = ar.result
            def buff = new Buffer(1000)
            10.times {
                asyncFile.read(buff, it * 100, it * 100, 100) { ar2 ->
                    if (ar2.succeeded()) {
                        println "Read ok!"
                        // etc
                    } else {
                        log.error("Failed to write", ar2.exception)
                    }
                }
            }
        } else {
            log.error("Failed to open file", ar.exception)
        }
    }


### Flushing data to underlying storage.

If the `AsyncFile` was not opened with `flush = true`, then you can manually flush any writes from the OS cache by calling the `flush` method.

This method can also be called with an handler which will be called when the flush is complete.

### Using AsyncFile as `ReadStream` and `WriteStream`

Use the methods `getReadStream` and `getWriteStream` to get read and write streams. You can then use them with a pump to pump data to and from other read and write streams.

Here's an example of pumping data from a file on a client to a HTTP request:

    def client = vertx.createHttpClient(host: "foo.com")

    vertx.fileSystem.open("some-file.dat") { ar ->
        if (ar.succeeded) {
            def request = client.put("/uploads") { resp ->
                println "Received response: ${resp.statusCode}"
            }
            def asyncFile = ar.result
            def rs = asyncFile.getReadStream()
            request.chunked = true
            Pump.createPump(rs, request).start()
            rs.endHandler{
                // File sent, end HTTP requuest
                request.end()
            }
        } else {
            log.error("Failed to open file", ar.exception)
        }
    }

### Closing an AsyncFile

To close an AsyncFile call the `close` method. Closing is asynchronous and if you want to be notified when the close has been completed you can specify a handler method as an argument.








