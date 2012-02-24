
[TOC]

# Writing Verticles

We previously discussed how a verticle is the unit of deployment in vert.x. Let's look in more detail about how to write a verticle.

As an example we'll write a simple TCP echo server. The server just accepts connections and any data received by it is echoed back on the connection.

Copy the following into a text editor and save it as `server.js`

    load('vertx.js')

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
      new vertx.Pump(sock, sock).start();
    }).listen(1234, 'localhost');

    function vertxStop() {
      server.close();
    }
    
Now, go to the directory where you saved the file and type

    vertx run server.js
    
The server will now be running. Connect to it using telnet:

    telnet localhost 1234
    
And notice how data you send (and hit enter) is echoed back to you.           

Congratulations! You've written your first verticle.
        
## Loading other scripts.

To load other scripts from within your verticle you use the `load` function. The argument to `load` is the name of the script you want to load. You can use `load` to load any other scripts in your verticle, or to load system scripts such as `vertx.js` which are always available to verticles.

If you want to access the vert.x core API from within your verticle (which you almost certainly want to do), you need to call `load('vertx.js')` at the top of your script. Normally this will be the first thing at the top of your verticle main. `vert.js` is just the name of the script that contains the core API.

## Verticle clean-up

The optional function `vertxStop` is called when the verticle is undeployed. You should always provide such a function if your verticle needs to do any clean-up, such as shutting down servers or clients or unregistering handlers. 
        
## Getting Configuration in a Verticle

If JSON configuration has been passed when deploying a verticle from either the command line using `vertx run` or `vertx deploy` and specifying a configuration file, or when deploying programmatically, that configuration is available to the verticle in the `vertx.config` variable. For example:

    var config = vertx.config;
    
    stdout.println("Config is " + JSON.stringify(config));
    
The config returned is a JSON object. You can use this object to configure the verticle. Allowing verticles to be configured in a consistent way like this allows configuration to be easily passed to them irrespective of the language.

## Logging from a Verticle

Each verticle is given its own logger. To get a reference to it invoke the `vertx.getLogger` function:

    var logger = vertx.getLogger;
    
    logger.info("I am logging something");
    
The logger has the functions:

* trace
* debug
* info
* warn
* error
* fatal           

Which have the normal meanings you would expect.

The log files by default go in a file called `vertx.log` in the system temp directory. On my Linux box this is `\tmp`.

For more information on configuring logging, please see the main manual.
   
# Deploying and Undeploying Verticles Programmatically

You can deploy and undeploy verticles programmatically from inside another verticle. Any verticles deployed programmatically inherit the path of the parent verticle. 

## Deploying a simple verticle

To deploy a verticle programmatically call the function `vertx.deployVerticle`. The return value of `vertx.deployVerticle` is the unique id of the deployment, which can be used later to undeploy the verticle.

To deploy a single instance of a verticle :

    vertx.deployVerticle('my_verticle.js');    
    
## Passing configuration to a verticle programmatically   
  
JSON configuration can be passed to a verticle that is deployed programmatically. Inside the deployed verticle the configuration is accessed with the `vertx.getConfig` function. For example:

    var config = { name: 'foo', age: 234 };
    vertx.deployVerticle('my_verticle.js', config); 
            
Then, in `my_verticle.js` you can access the config via `vertx.getConfig` as previously explained.
    
## Using a Verticle to co-ordinate loading of an application

If you have an appplication that is composed of multiple verticles that all need to be started at application start-up, then you can use another verticle that maintains the application configuration and starts all the other verticles. You can think of this as your application starter verticle.

For example, you could create a verticle `app.js` as follows:

    // Application config
    
    var appConfig = {
        verticle1Config: {
            // Config for verticle1
        },
        verticle2Config: {
            // Config for verticle2
        }, 
        verticle3Config: {
            // Config for verticle3
        },
        verticle4Config: {
            // Config for verticle4
        },
        verticle5Config: {
            // Config for verticle5
        }  
    }  
    
    // Start the verticles that make up the app  
    
    vertx.deployVerticle("verticle1.js", appConfig.verticle1Config);
    vertx.deployVerticle("verticle2.js", appConfig.verticle2Config, 5);
    vertx.deployVerticle("verticle3.js", appConfig.verticle3Config);
    vertx.deployWorkerVerticle("verticle4.js", appConfig.verticle4Config);
    vertx.deployWorkerVerticle("verticle5.js", appConfig.verticle5Config, 10);
        
        
Then you can start your entire application by simply running:

    vertx run app.js
    
or
    
    vertx deploy app.js
                        
## Specifying number of instances

By default, when you deploy a verticle only one instance of the verticle is deployed. If you want more than one instance to be deployed, e.g. so you can scale over your cores better, you can specify the number of instances as follows:

    vertx.deployVerticle('my_verticle.js', null, 10);   
  
The above example would deploy 10 instances.

## Getting Notified when Deployment is complete

The actual verticle deployment is asynchronous and might not complete until some time after the call to `deployVerticle` has returned. If you want to be notified when the verticle has completed being deployed, you can pass a handler as the final argument to `deployVerticle`:

    vertx.deployVerticle('my_verticle.js', null, 10, function() {
        log.info("It's been deployed!");
    });  
    
## Deploying Worker Verticles

The `vertx.deployVerticle` method deploys standard (non worker) verticles. If you want to deploy worker verticles use the `vertx.deployWorkerVerticle` function. This function takes the same parameters as `vertx.deployVerticle` with the same meanings.

## Undeploying a Verticle

Any verticles that you deploy programmatically from within a verticle, and all of their children are automatically undeployed when the parent verticle is undeployed, so in most cases you will not need to undeploy a verticle manually, however if you do want to do this, it can be done by calling the function `vertx.undeployVerticle` passing in the deployment id that was returned from the call to `vertx.deployVerticle`

    var deploymentID = vertx.deployVerticle('my_verticle.js');    
    
    vertx.undeployVerticle(deploymentID);    

            
# The Event Bus

The event bus is the nervous system of vert.x.

It allows verticles to communicate with each other irrespective of what language they are written in, and whether they're in the same vert.x instance, or in a different vert.x instance. It even allows client side JavaScript running in a browser to communicate on the same event bus. (More on that later).

It creates a distributed polyglot overlay network spanning multiple server nodes and multiple browsers.

The event bus API is incredibly simple. It basically involves registering handlers, unregistering handlers and sending messages.

First some theory:

## The Theory

### Addressing

Messages are sent on the event bus to an *address*.

Vert.x doesn't bother with any fancy addressing schemes. In vert.x an address is simply a string, any string is valid. However it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.

Some examples of valid addresses are `europe.news.feed1`, `acme.games.pacman`, `sausages`, and `X`. 

### Handlers

A handler is a thing that receives messages from the bus. You register a handler at an address.

The handler will receive any messages that are sent to the address. Many different handlers from the same or different verticles can be registered at the same address. A single handler can be registered by the verticle at many different addresses.

Since many handlers can subscribe to the address, this is an implementation of the messaging pattern called *Publish-Subscribe Messaging*.

When a message is received in a handler, and has been *processed*, the receiver can optionally decide to reply to the message. If they do so, and the message was sent specifying a reply handler, that reply handler will be called.

When the reply is received back at the sender, it too can be replied to. This can be repeated ad-infinitum, and allows a dialog to be set-up between two different verticles.

This is a common messaging pattern called the *Request-Response* pattern.

### Sending messages

You send a message by specifying the address and telling the event bus to send it there. The event bus will then deliver the message to any handlers registered at that address.

If multiple vert.x instances are clustered together, the message will be delivered to any matching handlers irrespective of what vert.x instance they reside on.  

As previously mentioned, when sending a message you can specify an optional reply handler which will be invoked once the message has reached a handler and the recipient has replied to it.

*All messages in the event bus are transient, and in case of failure of all or parts of the event bus, there is a possibility messages will be lost. If your application cares about lost messages, you should code your handlers to be idempotent, and your senders to retry after recovery.*

If you want to persist your messages you can use a persistent work queue busmod for that.

Messages that you send on the event bus can be as simple as a string, a number or a boolean. You can also send vert.x buffers or JSON messages.

It's highly recommended you use JSON messages to communicate between verticles. JSON is easy to create and parse in all the languages that vert.x supports.

## Event Bus API

Let's jump into the API

### Registering and Unregistering Handlers

To set a message handler on the address `test.address`, you do the following:

    var eb = vertx.EventBus;
    
    var myHandler = function(message)) {
      log.info('I received a message ' + message);
    }
    
    eb.registerHandler('test.address', myHandler);
    
It's as simple as that. The handler will then receive any messages sent to that address.

When you register a handler on an address and you're in a cluster it can take some time for the knowledge of that new handler to be propagated across the entire cluster. If you want to be notified when that has completed you can optionally specify another function to the `registerHandler` function as the third argument. This function will then be called once the information has reached all nodes of the cluster. E.g. :

    eb.registerHandler('test.address', myHandler, function() {
        log.info('Yippee! The handler info has been propagated across the cluster');
    });

To unregister a handler it's just as straightforward. You simply call `unregisterHandler` passing in the address and the handler:

    eb.unregisterHandler('test.address', myHandler);    
    
A single handler can be registered multiple times on the same, or different addresses so in order to identify it uniquely you have to specify both the address and the handler. 

As with registering, when you unregister a handler and you're in a cluster it can also take some time for the knowledge of that unregistration to be propagated across the entire to cluster. If you want to be notified when that has completed you can optionally specify another function to the registerHandler as the third argument. E.g. :

    eb.unregisterHandler('test.address', myHandler, function() {
        log.info('Yippee! The handler unregister has been propagated across the cluster');
    });
    
*Make sure you unregister any handlers in the vertxStop() method of your verticle, to avoid leaking handlers*    

### Sending messages

Sending a message is also trivially easy. Just send it specifying the address you want to send it to, for example:

    eb.send('test.address', 'hello world');
    
That message will then be delivered to any handlers registered against the address `test.address`. If you are running vert.x in cluster mode then it will also be delivered to any handlers on that address irrespective of what vert.x instance they are in.

The message you send can be any of the following types: 

* number
* string
* boolean
* JSON object
* Vert.x Buffer

Vert.x buffers and JSON objects are copied before delivery if they are delivered in the same JVM, so different verticles can't access the exact same object instance.

Here are some more examples:

Send some numbers:

    eb.send('test.address', 1234);    
    eb.send('test.address', 3.14159);        
    
Send a boolean:

    eb.send('test.address', true);        
    
Send a JSON object:

    var myObj = {
      name: 'Tim',
      address: 'The Moon',
      age: 457    
    }
    eb.send('test.address', myObj); 
    
Null messages can also be sent:

    eb.send('test.address', null);  
    
It's a good convention to have your verticles communicating using JSON.

### Replying to messages

Sometimes after you send a message you want to receive a reply from the recipient. This is known as the *request-response pattern*.

To do this you send a message, and specify a reply handler as the third argument. When the receiver receives the message they are passed a replier function as the second parameter to the handler. When this function is invoked it causes a reply to be sent back to the sender where the reply handler is invoked. An example will make this clear:

The receiver:

    var myHandler = function(message, replier) {
      log.info('I received a message ' + message);
      
      // Do some stuff
      
      // Now reply to it
      
      replier('This is a reply');
    }
    
    eb.registerHandler('test.address', myHandler);
    
The sender:

    eb.send('test.address', 'This is a message', function(reply) {
        log.info('I received a reply ' + reply);
    });
    
It is legal also to send an empty reply or null reply.

The replies themselves can also be replied to so you can create a dialog between two different verticles consisting of multiple rounds.

## Distributed event bus

To make each vert.x instance on your network participate on the same event bus, start each vert.x instance with the `-cluster` command line switch.

See the chapter in the main manual on *running vert.x* for more information on this. 

Once you've done that, any vert.x instances started in cluster mode will merge to form a distributed event bus.   
      
# Shared Data

Sometimes it makes sense to allow different verticles instances to share data in a safe way. Vert.x allows simple *Map* and *Set* data structures to be shared between verticles.

There is a caveat: To prevent issues due to mutable data, vert.x only allows simple immutable types such as number, boolean and string or Buffer to be used in shared data. With a Buffer, it is automatically copied when retrieved from the shared data, so different verticle instances never see the same object instance.

Currently data can only be shared between verticles in the *same vert.x instance*. In later versions of vert.x we aim to extend this to allow data to be shared by all vert.x instances in the cluster.

## Shared Maps

To use a shared map to share data between verticles first we get a reference to the map, and then we just use standard `put` and `get` to put and get data from the map:

    var map = vertx.shareddata.getMap('demo.mymap');
    
    map.put('some-key', 'some-value');
    
And then, in a different verticle:

    var map = vertx.shareddata.getMap('demo.mymap');
    
    log.info('value of some-key is ' + map.get('some-key');
    
**TODO** More on map API
    
## Shared Sets

To use a shared set to share data between verticles first we get a reference to the set.

    var set = vertx.shareddata.getSet('demo.myset');
    
    set.add('some-value');
    
And then, in a different verticle:

    var set = vertx.shareddata.getSet('demo.myset');
    
    // Do something with the set    
        
**TODO** - More on set API

API - atomic updates etc    

# Buffers

**TODO**

# JSON

**TODO**

# Delayed and Periodic Tasks

It's very common in vert.x to want to perform an action after a delay, or periodically.

In standard verticles you can't just make the thread sleep to introduce a delay, as that will block the event loop thread.

Instead you use vert.x timers. Timers can be *one-shot* or *periodic*. We'll discuss both

## One-shot Timers

A one shot timer calls an event handler after a certain delay, expressed in milliseconds. 

To set a timer to fire once you use the `vertx.setTimer` function passing in the delay and the handler

    vertx.setTimer(1000, function() {
        log.info('And one second later this is printed'); 
    });
    
    log.info('First this is printed');
     
## Periodic Timers

You can also set a timer to fire periodically by using the `setPeriodic` function. There will be an initial delay equal to the period. The return value of `setPeriodic` is a unique timer id (number). This can be later used if the timer needs to be cancelled. The argument passed into the timer event handler is also the unique timer id:

    var id = vertx.setTimer(1000, function(id) {
        log.info('And every second this is printed'); 
    });
    
    log.info('First this is printed');
    
## Cancelling timers

To cancel a periodic timer, call the `cancelTimer` function specifying the timer id. For example:

    var id = vertx.setTimer(1000, function(id) {        
    });
    
    // And immediately cancel it
    
    vertx.cancelTimer(id);
    
Or you can cancel it from inside the event handler. The following example cancels the timer after it has fired 10 times.

    var count = 0;
    
    vertx.setTimer(1000, function(id) {
        log.info('In event handler ' + count); 
        count++;
        if (count === 10) {
            vertx.cancelTimer(id);
        }
    });         
      
    
# Writing TCP Servers and Clients

Creating TCP servers and clients is incredibly easy with vert.x.

## Net Server

### Creating a Net Server

To create a TCP server we simply create an instance of vertx.net.NetServer.

    var server = new vertx.NetServer();
    
### Start the Server Listening    
    
To tell that server to listen for connections we do:    

    var server = new vertx.NetServer();

    server.listen(1234, 'myhost');
    
The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If it is ommitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


### Getting Notified of Incoming Connections
    
Just having a TCP server listening creates a working server that you can connect to (try it with telnet!), however it's not very useful since it doesn't do anything with the connections.

To be notified when a connection occurs we need to call the `connectHandler` function of the server, passing in a handler. The handler will be called when a connection is made:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
        log.info('A client has connected!');
    })  

    server.listen(1234, 'localhost');
    
That's a bit more interesting. Now it displays 'A client has connected!' every time a client connects.   

The return value of the `connectHandler` method is the server itself, so multiple invocations can be chained together. That means we can rewrite the above as:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
        log.info('A client has connected!');
    }).listen(1234, 'localhost');
    
or 

    new vertx.NetServer().connectHandler(function(sock) {
        log.info('A client has connected!');
    }).listen(1234, 'localhost');
    
    
This is a common pattern throughout the vert.x API.  
 

### Closing a Net Server

To close a net server just call the `close` function.

    server.close();

The close is actually asynchronous and might not complete until some time after the `close` function has returned. If you want to be notified when the actual close has completed then you can pass in a handler to the `close` function.

This handler will then be called when the close has fully completed.
 
    server.close(function() {
      log.info('The server is now fully closed.');
    });
    
    
### NetServer Properties

NetServer has a set of properties you can set which affect its behaviour. Firstly there are bunch of properties used to tweak the TCP parameters, in most cases you won't need to set these:

* `setTCPNoDelay(tcpNoDelay)` If `tcpNoDelay` is true then [Nagle's Algorithm](http://en.wikipedia.org/wiki/Nagle's_algorithm) is disabled. If false then it is enabled.

* `setSendBufferSize(size)` Sets the TCP send buffer size in bytes.

* `setReceiveBufferSize(size)` Sets the TCP receive buffer size in bytes.

* `setTCPKeepAlive(keepAlive)` if `keepAlive` is true then [TCP keep alive](http://en.wikipedia.org/wiki/Keepalive#TCP_keepalive) is enabled, if false it is disabled. 

* `setReuseAddress(reuse)` if `reuse` is true then addresses in TIME_WAIT state can be reused after they have been closed.

* `setSoLinger(linger)`

* `setTrafficClass(trafficClass)`

NetServer has a further set of properties which are used to configure SSL. We'll discuss those later on.


### Handling Data

So far we have seen how to create a NetServer, and accept incoming connections, but not how to do anything interesting with the connections. Let's remedy that now.

When a connection is made, the connect handler is called passing in an instance of `NetSocket`. This is a socket-like interface to the actual connection, and allows you to read and write data as well as do various other things like close the socket.


#### Reading Data from the Socket

To read data from the socket you need to set the `dataHandler` on the socket. This handler will be called with a `Buffer` every time data is received on the socket. You could try the following code and telnet to it to send some data:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
        sock.dataHandler(function(buffer) {
            log.info('I received ' + buffer.length() + ' bytes of data');
        });
      
    }).listen(1234, 'localhost');
    
#### Writing Data to a Socket

To write data to a socket, you invoke the `write` function. This function can be invoked in a few ways:

With a single buffer:

    var myBuffer = new vertx.Buffer(...);
    sock.write(myBuffer);
    
A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    sock.write('hello');    
    
A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.     

    sock.write('hello', 'UTF-16');
    
The `write` function is asynchronous and always returns immediately after the write has been queued.

The actual write might occur some time later. If you want to be informed when the actual write has happened you can pass in a function as a final argument.

This function will then be invoked when the write has completed:

    sock.write('hello', function() {
        log.info('It has actually been written');
    });

Let's put it all together.

Here's an example of a simple TCP echo server which simply writes back (echoes) everything that it receives on the socket:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
        sock.dataHandler(function(buffer) {
            sock.write(buffer);
        });
      
    }).listen(1234, 'localhost');
    
### Closing a socket

You can close a socket by invoking the `close` method. This will close the underlying TCP connection.

### Closed Handler

If you want to be notified when a socket is closed, you can set the `closedHandler':


    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
        
        sock.closedHandler(function() {        
            log.info('The socket is now closed');            
        });
    });

The closed handler will be called irrespective of whether the close was initiated by the client or server.

### Exception handler

You can set an exception handler on the socket that will be called if an exception occurs:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
        
        sock.exceptionHandler(function() {        
            log.error('Oops. Something went wrong');            
        });
    });

    
### Read and Write Streams

NetSocket also can at as a `ReadStream` and a `WriteStream`. This allows flow control to occur on the connection and the connection data to be pumped to and from other object such as HTTP requests and responses, websockets and asynchronous files.

This will be discussed in depth in the chapter on streams and pumps.

## Scaling TCP Servers

A verticle instance is strictly single threaded.

If you create a simple TCP server and deploy a single instance of it then all the handlers for that server are always executed on the same event loop (thread).

This means that if you are running on a server with a lot of cores, and you only have this one instance deployed then you will have at most one core utilised on your server! That's not very good, right?

To remedy this you can simply deploy more instances of the verticle in the server, e.g.

    vertx deploy echo_server.js -instances 20
    
The above would deploy 20 instances of echo_server.js to a locally running vert.x instance.

Once you do this you will find the echo server works functionally identically to before, but, *as if by magic*, all your cores on your server can be utilised and more work can be handled.

At this point you might be asking yourself *'Hold on, how can you have more than one server listening on the same host and port? Surely you will get port conflicts as soon as you try and deploy more than one instance?'*

*Vert.x does a little magic here*.

When you deploy another server on the same host and port as an existing server it doesn't actually try and create a new server listening on the same host/port.

Instead it internally maintains just a single server, and, as incoming connections arrive it distributes them in a round-robin fashion to any of the connect handlers set by the verticles.

Consequently vert.x TCP servers can scale over available cores while each vert.x verticle instance remains strictly single threaded, and you don't have to do any special tricks like writing load-balancers in order to scale your server on your multi-core machine.
    
## NetClient

A NetClient is used to make TCP connections to servers.

### Creating a Net Client

To create a TCP client we simply create an instance of `vertx.net.NetClient`.

    var client = new vertx.NetClient();

### Making a Connection

To actually connect to a server you invoke the `connect` method:

    var client = new vertx.NetClient();
    
    client.connect(1234, 'localhost', function(sock) {
        log.info('We have connected');
    });
    
The connect method takes the port number as the first parameter, followed by the hostname or ip address of the server. The third parameter is a connect handler. This handler will be called when the connection actually occurs.

The argument passed into the connect handler is an instance of `NetSocket`, exactly the same as what is passed into the server side connect handler. Once given the `NetSocket` you can read and write data from the socket in exactly the same way as you do on the server side.

You can also close it, set the closed handler, set the exception handler and use it as a `ReadStream` or `WriteStream` exactly the same as the server side `NetSocket`.

### Catching exceptions on the Net Client

You can set a connection handler on the `NetClient`. This will catch any exceptions that occur during connection.

    var client = new vertx.NetClient();
    
    client.exceptionHandler(function(ex) {
      log.info('Cannot connect since the host does not exist!');
    });
    
    client.connect(4242, 'host-that-doesnt-exist', function(sock) {
      log.info('this won't get called');
    });


### Configuring Reconnection

A NetClient can be configured to automatically retry connecting or reconnecting to the server in the event that it cannot connect or has lost its connection. This is done by invoking the functions `setReconnectAttempts` and `setReconnectInterval`:

    var client = new vertx.NetClient();
    
    client.setReconnectAttempts(1000);
    
    client.setReconnectInterval(500);
    
`ReconnectAttempts` determines how many times the client will try to connect to the server before giving up. A value of `-1` represents an infinite number of times. The default value is `0`. I.e. no reconnection is attempted.

`ReconnectInterval` detemines how long, in milliseconds, the client will wait between reconnect attempts. The default value is `1000`.

If an exception handler is set on the client, and reconnect attempts is not equal to `0`. Then the exception handler will not be called until the client gives up reconnecting.


### NetClient Properties

Just like `NetServer`, `NetClient` also has a set of TCP properties you can set which affect its behaviour. They have the same meaning as those on `NetServer`.

`NetClient` also has a further set of properties which are used to configure SSL. We'll discuss those later on.

## SSL Servers

Net servers can also be configured to work with [Transport Layer Security](http://en.wikipedia.org/wiki/Transport_Layer_Security) (previously known as SSL).

When a `NetServer` is working as an SSL Server the API of the `NetServer` and `NetSocket` is identical compared to when it working with standard sockets. Getting the server to use SSL is just a matter of configuring the `NetServer` before `listen` is called.

To enabled ssl the function `setSSL(true)` must be called on the Net Server.

The server must also be configured with a *key store* and an optional *trust store*.

These are both *Java keystores* which can be managed using the [keytool](http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/keytool.html) utility which ships with the JDK.

The keytool command allows you to create keystores, and import and export certificates from them.

The key store should contain the server certificate. This is mandatory - the client will not be able to connect to the server over ssl if the server does not have a certificate.

The key store is configured on the server using the `setKeyStorePath` and `setKeyStorePassword` functions.

The trust store is optional and contains the certificates of any clients it should trust. This is only used if client authentication is required. 

To configure a server to use server certificates only:

    var server = new vertx.NetServer()
                   .setSSL(true)
                   .setKeyStorePath('/path/to/your/keystore/server-keystore.jks')
                   .setKeyStorePassword('password');
    
Making sure that `server-keystore.jks` contains the server certificate.

To configure a server to also require client certificates:

    var server = new vertx.NetServer()
                   .setSSL(true)
                   .setKeyStorePath('/path/to/your/keystore/server-keystore.jks')
                   .setKeyStorePassword('password')
                   .setTrustStorePath('/path/to/your/truststore/server-truststore.jks')
                   .setTrustStorePassword('password')
                   .setClientAuthRequired(true);
    
Making sure that `server-truststore.jks` contains the certificates of any clients who the server trusts.

If `clientAuthRequired` is set to `true` and the client cannot provide a certificate, or it provides a certificate that the server does not trust then the connection attempt will not succeed.

## SSL Clients

Net Clients can also be easily configured to use SSL. They have the exact same API when using SSL as when using standard sockets.

To enable SSL on a `NetClient` the function `setSSL(true)` is called.

If the `setTrustAll(true)` is invoked on the client, then the client will trust all server certificates. The connection will still be encrypted but this mode is vulnerable to 'man in the middle' attacks. I.e. you can't be sure who you are connecting to. Use this with caution. Default value is `false`.

If `setTrustAll(true)` has not been invoked then a client trust store must be configured and should contain the certificates of the servers that the client trusts.

The client trust store is just a standard Java key store, the same as the key stores on the server side. The client trustore location is set by using the function `setTrustStorePath` on the `NetClient`. If a server presents a certificate during connection which is not in the client trust store, the connection attempt will not succeed.

If the server requires client authentication then the client must present its own certificate to the server when connecting. This certificate should reside in the client key store. Again its just a regular Java key store. The client keystore location is set by using the function `setKeyStorePath` on the `NetClient`. 

To configure a client to trust all server certificates (dangerous):

    var client = new vertx.NetClient()
                   .setSSL(true)
                   .setTrustAll(true);
    
To configure a client to only trust those certificates it has in its trust store:

    var client = new vertx.NetClient()
                   .setSSL(true)
                   .setTrustStorePath('/path/to/your/client/truststore/client-truststore.jks')
                   .setTrustStorePassword('password');
                   
To configure a client to only trust those certificates it has in its trust store, and also to supply a client certificate:

    var client = new vertx.NetClient()
                   .setSSL(true)
                   .setTrustStorePath('/path/to/your/client/truststore/client-truststore.jks')
                   .setTrustStorePassword('password')
                   .setClientAuthRequired(true)
                   .setKeyStorePath('/path/to/keystore/holding/client/cert/client-keystore.jks')
                   .setKeyStorePassword('password');
                     
 

# Flow Control - Streams and Pumps

There are several objects in vert.x that allow data to be read from and written to in the form of Buffers.

All operations in the vert.x API are non blocking; calls to write data return immediately and writes are internally queued.

It's not hard to see that if you write to an object faster than it can actually write the data to its underlying resource then the write queue could grow without bound - eventually resulting in exhausting available memory.

To solve this problem a simple flow control capability is provided by some objects in the vert.x API.

Any flow control aware object that can be written to is said to implement `ReadStream`, and any flow control object that can be read from is said to implement `WriteStream`.

Let's take an example where we want to read from a `ReadStream` and write the data to a `WriteStream`.

An example would be reading from a `NetSocket` and writing to an `AsyncFile` on disk. A naive way to do this would be to directly take the data that's been read and immediately write it to the NetSocket, for example:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
        vertx.FileSystem.open('some_file.dat', function(err, res) {
            sock.dataHandler(function(buffer) {
      
                // Stream all data directly to the disk file:
                      
                asyncFile.write(buffer); 
            });
        });
        
    }).listen(1234, 'localhost');
    
There's a problem with the above example: If data is read from the socket faster than it can be written to the underlying disk file, it will build up in the write queue of the AsyncFile, eventually running out of RAM.

It just so happens that `AsyncFile` implements `WriteStream`, so we can check if the `WriteStream` is full before writing to it:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
        vertx.FileSystem.open('some_file.dat', function(err, asyncFile) {
            sock.dataHandler(function(buffer) {
      
                if (!asyncFile.writeQueueFull()) {      
                    asyncFile.writeBuffer(buffer); 
                }
            });
        });
          
    }).listen(1234, 'localhost');
    
This example won't run out of RAM but we'll end up losing data if the write queue gets full. What we really want to do is pause the `NetSocket` when the `AsyncFile` `WriteQueue` is full. Let's do that:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
        vertx.FileSystem.open('some_file.dat', function(err, asyncFile) {
            sock.dataHandler(function(buffer) {
      
                if (!asyncFile.writeQueueFull()) {      
                    asyncFile.writeBuffer(buffer); 
                } else {
                    sock.pause();
                }
            });
        });
        
      });
      
    }).listen(1234, 'localhost');

We're almost there, but not quite. The `NetSocket` now gets paused when the file is full, but we also need to *unpause* it when the file write queue has processed its backlog:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
        vertx.FileSystem.open('some_file.dat', function(err, asyncFile) {
            sock.dataHandler(function(buffer) {
      
                if (!asyncFile.writeQueueFull()) {      
                    asyncFile.writeBuffer(buffer); 
                } else {
                    sock.pause();
                    
                    asyncFile.drainHandler(function() {
                        sock.resume();
                    });
                }
            });
        });    
      
    }).listen(1234, 'localhost');

And there we have it. The `drainHandler` event handler will get called when the write queue is ready to accept more data, this resumes the `NetSocket` which allows it to read more data.

It's very common to want to do this when writing vert.x applications, so we provide a helper class called `Pump` which does all this hard work for you. You just feed it the `ReadStream` and the `WriteStream` and it tell it to start:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
        var asyncFile = vertx.FileSystem.open('some_file.dat', function(err, asyncFile) {        
            var pump = new vertx.Pump(sock, asyncFile);      
            pump.start();
        });        
      
    }).listen(1234, 'localhost');
    
Which does exactly the same thing as the more verbose example.

Let's look at the methods on `ReadStream` and `WriteStream` in more detail:

## ReadStream

`ReadStream` is implemented by `AsyncFile`, `HttpClientResponse`, `HttpServerRequest`, `WebSocket`, `NetSocket` and `SockJSSocket`.

Functions:

* `dataHandler(handler)`: set a handler which will receive data from the `ReadStream`. As data arrives the handler will be passed a Buffer.
* `pause()`: pause the handler. When paused no data will be received in the `dataHandler`.
* `resume()`: resume the handler. The handler will be called if any data arrives.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `ReadStream`.
* `endHandler(handler)`: Will be called when end of stream is reached. This might be when EOF is reached if the `ReadStream` represents a file, or when end of request is reached if it's an HTTP request, or when the connection is closed if it's a TCP socket.

## WriteStream

`WriteStream` is implemented by `AsyncFile`, `HttpClientRequest`, `HttpServerResponse`, `WebSocket`, `NetSocket` and `SockJSSocket`

Functions:

* `writeBuffer(buffer)`: write a Buffer to the `WriteStream`. This method will never block. Writes are queued internally and asynchronously written to the underlying resource.
* `setWriteQueueMaxSize(size)`: set the number of bytes at which the write queue is considered *full*, and the function `writeQueueFull()` returns `true`. Note that, even if the write queue is considered full, if `writeBuffer` is called the data will still be accepted and queued.
* `writeQueueFull()`: returns `true` if the write queue is considered full.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `WriteStream`.
* `drainHandler(handler)`: The handler will be called if the `WriteStream` is considered no longer full.

## Pump

Instances of `Pump` have the following methods:

* `start()`. Start the pump.
* `stop()`. Stops the pump. When the pump starts it is in stopped mode.
* `setWriteQueueMaxSize()`. This has the same meaning as `setWriteQueueMaxSize` on the `WriteStream`.
* `getBytesPumped()`. Returns total number of bytes pumped.

A pump can be started and stopped multiple times.

# Writing HTTP Servers and Clients

## Writing HTTP servers

Vert.x allows you to easily write full featured, highly performant and scalable HTTP servers.

### Creating an HTTP Server

To create an HTTP server you simply create an instance of vertx.net.HttpServer.

    var server = new vertx.HttpServer();
    
### Start the Server Listening    
    
To tell that server to listen for incoming requests you use the `listen` method:

    var server = new vertx.HttpServer();

    server.listen(8080, 'myhost');
    
The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If the hostname is ommitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


### Getting Notified of Incoming Requests
    
To be notified when a request arrives you need to set a request handler. This is done by calling the `requestHandler` function of the server, passing in the handler:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
      log.info('An HTTP request has been received');
    })  

    server.listen(8080, 'localhost');
    
This displays 'An HTTP request has been received!' every time an HTTP request arrives on the server. You can try it by running the verticle and pointing your browser at `http://localhost:8080`.

Similarly to `NetServer`, the return value of the `requestHandler` method is the server itself, so multiple invocations can be chained together. That means we can rewrite the above with:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
      log.info('An HTTP request has been received');
    }).listen(8080, 'localhost');
    
Or:

    new vertx.HttpServer().requestHandler(function(request) {
      log.info('An HTTP request has been received');
    }).listen(8080, 'localhost');
    
       
### Handling HTTP Requests

So far we have seen how to create an HTTPServer and be notified of requests. Lets take a look at how to handle the requests and do something useful with them.

When a request arrives, the request handler is called passing in an instance of `HttpServerRequest`. This object represents the server side HTTP request.

The handler is called when the headers of the request have been fully read. If the request contains a body, that body may arrive at the server some time after the request handler has been called.

It contains functions to get the URI, path, request headers and request parameters. It also contains a `response` property which is a reference to an object that represents the server side HTTP response for the object.

#### Request Method

The request object has a property `method` which is a string representing what HTTP method was requested. Possible values for `method` are: `GET`, `PUT`, `POST`, `DELETE`, `HEAD`, `OPTIONS`, `CONNECT`, `TRACE`, `PATCH`.

#### Request URI

The request object has a property `uri` which contains the full URI (Uniform Resource Locator) of the request. For example, if the request URI was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.uri` would contain the string `http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz`.

#### Request Path

The request object has a property `path` which contains the path of the request. For example, if the request URI was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.path` would contain the string `/a/b/c/page.html`
   
#### Request Query

The request object has a property `query` which contains the query of the request. For example, if the request URI was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.query` would contain the string `param1=abc&param2=xyz`    
        
#### Request Headers

The request headers are available using the `headers()` function on the request object. The return value of the function is just a JavaScript object. As we all know JavaScript objects are just hashes (associative arrays).

Here's an example that echoes the headers to the output of the response. Run it and point your browser at `http://localhost:8080` to see the headers.

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
      var headers = request.headers();  
    
      var str = '';
      for (var k in headers) {
        str = str.concat(k, ': ', headers[k], '\n');
      }
      
      request.response.end(str);
      
    }).listen(8080, 'localhost');
    
#### Request params

Similarly to the headers, the request parameters are available using the `params()` function on the request object. The return value of the function is just a hash too.     

Request parameters are sent on the request URI, after the path. For example if the URI was:

    http://localhost:8080/page.html?param1=abc&param2=xyz
    
Then the params hash would be the following JS object:

    { param1: 'abc', param2: 'xyz' }
    
#### Reading Data from the Request Body

Sometimes an HTTP request contains a request body that we want to read. As previously mentioned the request handler is called when only the headers of the request have arrived so the `HTTPServerRequest` object does not contain the body. This is because the body may be very large and we don't want to create problems with exceeding available memory.

To receive the body, you set the `dataHandler` on the request object. This will then get called every time a chunk of the request body arrives. Here's an example:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
      request.dataHandler(function(buffer) {
        log.info('I received ' + buffer.length() + ' bytes');
      });
      
    }).listen(8080, 'localhost'); 
    
The `dataHandler` may be called more than once depending on the size of the body.    

You'll notice this is very similar to how data from `NetSocket` is read. 

The request object implements the `ReadStream` interface so you can pump the request body to a `WriteStream`. See the chapter on streams and pumps for a detailed explanation. 

In many cases, you know the body is not large and you just one to receive it in one go. To do this you could do something like the following:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
      // Create a buffer to hold the body
      var body = new vertx.Buffer();  
    
      request.dataHandler(function(buffer) {
        // Append the chunk to the buffer
        body.appendBuffer(buffer);
      });
      
      request.endHandler(function() {
        // The entire body has now been received
        log.info('The total body received was ' + body.length() + ' bytes');
      });
      
    }).listen(8080, 'localhost');   
    
Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the request.

If the HTTP request is using HTTP chunking, then each HTTP chunk of the request body will correspond to a single call of the data handler.

It's a very common use case to want to read the entire body before processing it, so vert.x allows a `bodyHandler` to be set on the request object.

The body handler is called only once when the *entire* request body has been read.

*Beware of doing this with very large requests since the entire request body will be stored in memory.*

Here's an example using `bodyHandler`:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
      request.bodyHandler(function(body) {
        log.info('The total body received was ' + body.length() + ' bytes');
      });
      
    }).listen(8080, 'localhost');  
    
Simples, innit?    
    
### HTTP Server Responses 

As previously mentioned, the HTTP request object contains a property `response`. This is the HTTP response for the request. You use it to write the response back to the client.

### Setting Status Code and Message

To set the HTTP status code for the response use the `statusCode` property, e.g.

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
        request.response.statusCode = 404;
      
    }).listen(8080, 'localhost');  
    
You can also use the `statusMessage` property to set the status message. If you do not set the status message a default message will be used.    
  

#### Writing HTTP responses

To write data to an HTTP response, you invoke the `write` function. This function can be invoked multiple times before the response is ended. It can be invoked in a few ways:

With a single buffer:

    var myBuffer = ...
    request.response.write(myBuffer);
    
A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.response.write('hello');    
    
A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.     

    request.response.write('hello', 'UTF-16');
    
The `write` function is asynchronous and always returns immediately after the write has been queued.

The actual write might complete some time later. If you want to be informed when the actual write has completed you can pass in a function as a final argument. This function will then be invoked when the write has completed:

    request.response.write('hello', function() {
        log.info('It has actually been written');
    });  
    
If you are just writing a single string or Buffer to the HTTP response you can write it and end the response in a single call to the `end` function.   

The first call to `write` results in the response header being being written to the response.

Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the response, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry. 
   
#### Ending HTTP responses

Once you have finished with the HTTP response you must call the `end()` function on it.

This function can be invoked in several ways:

With no arguments, the response is simply ended. 

    request.response.end();
    
The function can also be called with a string or Buffer in the same way `write` is called. In this case it's just the same as calling write with a string or Buffer followed by calling `end` with no arguments. For example:

    request.response.end("That's all folks");

You can also optionally call `end` with a final boolean argument. If this argument is `true` then the underlying connection will be closed when the response has been written, otherwise the underlying connection will be left open.

    // End response and close connection
    request.response.end(true);
    
Or:

    // Write something, end response and close connection
    request.response.end("That's really all folks", true);
    

#### Response headers

Individual HTTP response headers can be written using the `putHeader` method. For example:

    request.response.putHeader('Content-Length', '0');    
    
Response headers must all be added before any parts of the response body are written.

If you wish to add several headers in one operation, just call `putHeaders` with a hash of the headers:

    request.response.putHeaders({ 'Content-Length' : '0', 'Some-Other-Header': 'abc'});
    
#### Chunked HTTP Responses and Trailers

Vert.x supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding). This allows the HTTP response body to be written in chunks, and is normally used when a large response body is being streamed to a client, whose size is not known in advance.

You put the HTTP response into chunked mode as follows:

    req.response.setChunked(true);
    
Default is non-chunked. When in chunked mode, each call to `response.write(...)` will result in a new HTTP chunk being written out.  

When in chunked mode you can also write HTTP response trailers to the response. These are actually written in the final chunk of the response.  

To write an individual trailer use the `putTrailer` method:

    request.response.putTrailer('Some-Trailer', 'some value'); 
    
If you wish to add several trailers in one operation, just call `putTrailers` with a hash of the trailers:

    request.response.putTrailers({ 'Some-Trailer' : 'some value', 'Some-Other-Trailer': 'wibble'});    


### Serving files directly disk

If you were writing a web server, one way to serve a file from disk would be to open it as an `AsyncFile` and pump it to the HTTP response. Or you could load it it one go using the file system API and write that to the HTTP response.

Alternatively, vert.x provides a feature where you can stream files directly from disk to the response bypassing user-space altogether.

This functionality is OS dependent and leverages the `sendfile` function to tell the kernel to directly serve the file. Using this operation can greatly reduce CPU utilisation.

To do this use the `sendfile` function on the HTTP response. Here's a simple HTTP web server that serves static files from the local `web` directory:

    var server = new vertx.HttpServer();

    server.requestHandler(function(req) {
      var file = '';
      if (req.path == '/') {
        file = 'index.html';
      } else if (req.path.indexOf('..') == -1) {
        file = req.path;
      }
      req.response.sendFile('web/' + file);   
    }).listen(8080, 'localhost');
    
*Note: If you use `sendfile` while using HTTPS it won't actually use the kernel `sendfile` function, since if the kernel is copying data directly from disk to socket it doesn't give us an opportunity to apply any encryption.*

**If you're going to write web servers using vert.x be careful that users cannot exploit the path to access files outside the directory from which you want to serve them.**

### Pumping Responses

Since the HTTP Response implements `WriteStream` you can pump to it from any `ReadStream`, e.g. an `AsyncFile`, `NetSocket` or `HttpServerRequest`.

Here's an example which echoes HttpRequest headers and body back in the HttpResponse. It uses a pump for the body, so it will work even if the HTTP request body is much larger than can fit in memory at any one time:

    var server = new vertx.HttpServer();

    server.requestHandler(function(req) {
      
      req.response.putHeaders(req.headers());
      
      var p = new Pump(req, req.response);
      p.start();
      
    }).listen(8080, 'localhost');
    
## Writing HTTP Clients

### Creating an HTTP Client

To create an HTTP client you simply create an instance of vertx.HttpClient

    var client = new vertx.HttpClient();
    
You set the port and hostname (or ip address) that the client will connect to using the `setHost` and `setPort` functions:

    var client = new vertx.HttpClient();
    client.setPort(8181);
    client.setHost('foo.com');
    
This, of course, can be chained:

    var client = new vertx.HttpClient()
                   .setPort(8181);
                   .setHost('foo.com');
                   
A single `HTTPClient` always connects to the same host and port. If you want to connect to different servers, create more instances.

The default value for hostname is `localhost`, and the default value for port is `80`.  

### Pooling and Keep Alive

By default the `HTTPClient` pools HTTP connections. As you make requests a connection is borrowed from the pool and returned when the HTTP response has ended.

If you do not want connections to be pooled you can call `setKeepAlive` with `false`:

    var client = new vertx.HttpClient()
                   .setPort(8181);
                   .setHost('foo.com').
                   .setKeepAlive(false);

In this case a new connection will be created for each HTTP request and closed once the response has ended.

You can set the maximum number of connections that the client will pool as follows:

    var client = new vertx.HttpClient()
                   .setPort(8181);
                   .setHost('foo.com').
                   .setMaxPoolSize(10);
                   
The default value is `1`.         

### Closing the client

Once you have finished with an HTTP client, you should close it:

    client.close();             
                         
### Making Requests

To make a request using the client you invoke one the methods named after the HTTP method that you want to invoke.

For example, to make a `POST` request:

    var client = new vertx.HttpClient();
    
    var request = client.post('http://localhost:8080/some-path/', function(resp) {
        log.info('Got a response, status code: ' + resp.statusCode);
    });
    
    request.end();
    
To make a PUT request use the `put` method, to make a GET request use the `get` method, etc.

Legal request methods are: `get`, `put`, `post`, `delete`, `head`, `options`, `connect`, `trace` and `patch`.

The general modus operandi is you invoke the appropriate method passing in the request URI as the first parameter, the second parameter is an event handler which will get called when the corresponding response arrives. The response handler is passed the client response object as an argument.

The return value from the appropriate request method is an `HTTPClientRequest` object. You can use this to add headers to the request, and to write to the request body. The request object implements `WriteStream`.

Once you have finished with the request you must call the `end` function.

If you don't know the name of the request method in advance there is a general `request` method which takes the HTTP method as a parameter:

    var client = new vertx.HttpClient();
    
    var request = client.request('POST', 'http://localhost:8080/some-path', function(resp) {
        log.info('Got a response, status code: ' + resp.statusCode);
    });
    
    request.end();
    
There is also a method called `getNow` which does the same as `get`, but automatically ends the request. This is useful for simple GETs which don't have a request body:

    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-path', function(resp) {
        log.info('Got a response, status code: ' + resp.statusCode);
    });

With `getNow` there is no return value.

#### Writing to the request body

Writing to the client request body has a very similar API to writing to the server response body.

To write data to an `HttpClientRequest` object, you invoke the `write` function. This function can be called multiple times before the request has ended. It can be invoked in a few ways:

With a single buffer:

    var myBuffer = ...
    request.write(myBuffer);
    
A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.write('hello');    
    
A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.     

    request.write('hello', 'UTF-16');
    
The `write` function is asynchronous and always returns immediately after the write has been queued. The actual write might complete some time later.

If you want to be informed when the actual write has completed you can pass in a function as a final argument. This function will be invoked when the write has completed:

    request.response.write('hello', function() {
        log.info('It has actually been written');
    });  
    
If you are just writing a single string or Buffer to the HTTP request you can write it and end the request in a single call to the `end` function.   

The first call to `write` results in the request header being being written to the request.

Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the request, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry. 


#### Ending HTTP requests

Once you have finished with the HTTP request you must call the `end` function on it.

This function can be invoked in several ways:

With no arguments, the response is simply ended. 

    request.end();
    
The function can also be called with a string or Buffer in the same way `write` is called. In this case it's just the same as calling write with a string or Buffer followed by calling `end` with no arguments.

You can also optionally call `end` with a final boolean argument. If this argument is `true` then the underlying connection will be closed when the response has been written, otherwise the underlying connection will be left open.

#### Writing Request Headers

To write headers to the request, use the `putHeader` method.

    var client = new vertx.HttpClient();
    
    var request = client.post('http://localhost:8080/some-path', function(resp) {
        log.info('Got a response, status code: ' + resp.statusCode);
    });
    
    request.putHeader('Some-Header', 'Some-Value');
    
These can be chained together as per the common vert.x API pattern:

    client.post('http://localhost:8080/some-uri', function(resp) {
        log.info('Got a response, status code: ' + resp.statusCode);
    }).putHeader('Some-Header', 'Some-Value')
      .putHeader('Some-Other-Header', 'Some-Other-Value')
      .end();

If you want to put more than one header at the same time, you can instead use the `putHeaders` function.
  
    client.post('http://localhost:8080/some-uri', function(resp) {
        log.info('Got a response, status code: ' + resp.statusCode);
    }).putHeaders({'Some-Header': 'Some-Value',
                   'Some-Other-Header': 'Some-Other-Value',
                   'Yet-Another-Header': 'Yet-Another-Value'})
      .end(); 
       
      

#### HTTP chunked requests

Vert.x supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding) for requests. This allows the HTTP request body to be written in chunks, and is normally used when a large request body is being streamed to the server, whose size is not known in advance.

You put the HTTP request into chunked mode as follows:

    request.setChunked(true);
    
Default is non-chunked. When in chunked mode, each call to `request.write(...)` will result in a new HTTP chunk being written out.  

### HTTP Client Responses

Client responses are received as an argument to the response handler that is passed into one of the request methods on the HTTP client.

The response object implements `ReadStream`, so it can be pumped to a `WriteStream` like any other `ReadStream`.

To query the status code of the response use the `statusCode` property. The `statusMessage` property contains the status message. For example:

 var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-path', function(resp) {
      log.info('server returned status code: ' + resp.statusCode);   
      log.info('server returned status message: ' + resp.statusMessage);   
    });

#### Reading Data from the Response Body

The API for reading a http client response body is very similar to the API for reading a http server request body.

Sometimes an HTTP response contains a request body that we want to read. Like an HTTP request, the client response handler is called when all the response headers have arrived, not when the entire response body has arrived.

To receive the response body, you set a `dataHandler` on the response object which gets called as parts of the HTTP response arrive. Here's an example:


    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-path', function(resp) {
      resp.dataHandler(function(buffer) {
        log.info('I received ' + buffer.length() + ' bytes');
      });    
    });

The response object implements the `ReadStream` interface so you can pump the response body to a `WriteStream`. See the chapter on streams and pump for a detailed explanation. 

The `dataHandler` can be called multiple times for a single HTTP response.

As with a server request, if you wanted to read the entire response body before doing something with it you could do something like the following:

    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-path', function(resp) {
      
      // Create a buffer to hold the entire response body
      var body = new vertx.Buffer();  
    
      resp.dataHandler(function(buffer) {
        // Add chunk to the buffer
        body.appendBuffer(buffer);
      });
      
      resp.endHandler(function() {
        // The entire response body has been received
        log.info('The total body received was ' + body.length() + ' bytes');
      });
      
    });
    
Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the response.

If the HTTP response is using HTTP chunking, then each chunk of the response body will correspond to a single call to the `dataHandler`.

It's a very common use case to want to read the entire body in one go, so vert.x allows a `bodyHandler` to be set on the response object.

The body handler is called only once when the *entire* response body has been read.

*Beware of doing this with very large responses since the entire response body will be stored in memory.*

Here's an example using `bodyHandler`:

    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-uri', function(resp) {
      
      resp.bodyHandler(function() {
        log.info('The total body received was ' + body.length() + ' bytes');
      });
      
    }); 
    
## Pumping Requests and Responses

The HTTP client and server requests and responses all implement either `ReadStream` or `WriteStream`. This means you can pump between them.

Here's a very simple proxy server which forwards a request to another server and forwards the response back to the client.

It uses two pumps - one to pump the server request to the client request, and another to pump the client response back to the server response. It uses pumps so it will work even if the HTTP request body is much larger than can fit in memory at any one time:

    var server = new vertx.HttpServer();
    
    var client = new vertx.HttpClient().setHost('some-other-server.com');

    server.requestHandler(function(req) {
    
        var clientReq = client.request(req.method, req.uri, function(clientResp) {
    
            req.response.status_code = clientResp.statusCode;
            req.response.putAllHeaders(clientResp.headers());
            
            var respPump = new Pump(clientResp, req.response);
            respPump.start();
            
            clientResp.endHandler(function() { req.response.end() });
        }

        clientReq.putAllHeaders(req.headers());
        
        var reqPump = new Pump(req, clientReq);
        
        reqPump.start();
        
        req.endHandler(function { clientReq.end() } );
      
    }).listen(8080, 'localhost');
    
### 100-Continue Handling

According to the [HTTP 1.1 specification](http://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html) a client can set a header `Expect: 100-Continue` and send the request header before sending the rest of the request body.

The server can then respond with an interim response status `Status: 100 (Continue)` to signify the client is ok to send the rest of the body.

The idea here is it allows the server to authorise and accept/reject the request before large amounts of data is sent. Sending large amounts of data if the request might not be accepted is a waste of bandwidth and ties up the server in reading data that it will just discard.

Vert.x allows you to set a `continueHandler` on the client request object. This will be called if the server sends back a `Status: 100 (Continue)` response to signify it is ok to send the rest of the request.

This is used in conjunction with the `sendHead` function to send the head of the request.

An example will illustrate this:

    var client = new vertx.HttpClient();
    
    var request = client.put('http://localhost:8080/some-path', function(resp) {
      
      resp.bodyHandler(function(resp) {
        log.info('Got a response ' + resp.statusCode);
      });
      
    });     
    
    request.putHeader('Expect', '100-Continue');
    
    request.continueHandler(function() {
        // OK to send rest of body
        
        request.write('Some data').end();
    });
    
    request.sendHead();

## HTTPS Servers

HTTPS servers are very easy to write using vert.x.

An HTTPS server has an identical API to a standard HTTP server. Getting the server to use HTTPS is just a matter of configuring the HTTP Server before `listen` is called.

Configuration of an HTTPS server is done in exactly the same way as configuring a `NetServer` for SSL. Please see SSL server chapter for detailed instructions.

## HTTPS Clients

HTTPS clients can also be very easily written with vert.x

Configuring an HTTP client for HTTPS is done in exactly the same way as configuring a `NetClient` for SSL. Please see SSL client chapter for detailed instructions. 

## Scaling HTTP servers

Scaling an HTTP or HTTPS server over multiple cores is as simple as deploying more instances of the verticle. For example:

    vertx deploy http_server.js -instances 20
    
The scaling works in the same way as scaling a `NetServer`. Please see the chapter on scaling Net Servers for a detailed explanation of how this works.

# Routing HTTP requests with Pattern Matching

Vert.x lets you route HTTP requests to different handlers based on pattern matching on the request path. It also enables you to extract values from the path and use them as parameters in the request.

This is particularly useful when developing REST-style web applications.

To do this you simply create an instance of `vertx.RouteMatcher` and use it as handler in an HTTP server. See the chapter on HTTP servers for more information on setting HTTP handlers. Here's an example:

    var server = new vertx.HttpServer();
    
    var routeMatcher = new vertx.RouteMatcher();
        
    server.requestHandler(routeMatcher).listen(8080, 'localhost');
    
## Specifying matches.    
    
You can then add different matches to the route matcher. For example, to send all GET requests with path `/animals/dogs` to one handler and all GET requests with path `/animals/cats` to another handler you would do:

    var server = new vertx.HttpServer();
    
    var routeMatcher = new vertx.RouteMatcher();
    
    routeMatcher.get('/animals/dogs', function(req) {
        req.response.end('You requested dogs');
    });
    
    routeMatcher.get('/animals/cats', function(req) {
        req.response.end('You requested cats');    
    });
        
    server.requestHandler(routeMatcher).listen(8080, 'localhost');
    
Corresponding methods exist for each HTTP method - `get`, `post`, `put`, `delete`, `head`, `options`, `trace`, `connect` and `patch`.

There's also an `all` method which applies the match to any HTTP request method.

The handler specified to the method is just a normal HTTP server request handler, the same as you would supply to the `requestHandler` method of the HTTP server.

You can provide as many matches as you like and they are evaulated in the order you added them, the first matching one will receive the request.

A request is sent to at most one handler.

## Extracting parameters from the path

If you want to extract parameters from the path, you can do this too, by using the `:` (colon) character to denote the name of a parameter. For example:

    var server = new vertx.HttpServer();
    
    var routeMatcher = new vertx.RouteMatcher();
    
    routeMatcher.put('/:blogname/:post', function(req) {        
        var blogName = req.params().blogname;
        var post = req.params().post;
        req.response.end('blogname is ' + blogName + ', post is ' + post);
    });
    
    server.requestHandler(routeMatcher).listen(8080, 'localhost');
    
Any params extracted by pattern matching are added to the map of request parameters.

In the above example, a PUT request to `/myblog/post1` would result in the variable `blogName` getting the value `myblog` and the variable `post` getting the value `post1`.

Valid parameter names must start with a letter of the alphabet and be followed by any letters of the alphabet or digits.

## Extracting params using Regular Expressions

Regular Expressions can be used to extract more complex matches. In this case capture groups are used to capture any parameters.

Since the capture groups are not named they are added to the request with names `param0`, `param1`, `param2`, etc. For example:

Corresponding methods exist for each HTTP method - `getWithRegEx`, `postWithRegEx`, `putWithRegEx`, `deleteWithRegEx`, `headWithRegEx`, `optionsWithRegEx`, `traceWithRegEx`, `connectWithRegEx` and `patchWithRegEx`.

There's also an `allWithRegEx` method which applies the match to any HTTP request method.

    var server = new vertx.HttpServer();
    
    var routeMatcher = new vertx.RouteMatcher();
    
    routeMatcher.putWithRegEx('\\/([^\\/]+)\\/([^\\/]+)', function(req) {        
        var first = req.params().param0
        var second = req.params().param1;
        // Do something
        req.response.end();
    });    
    
You can use regular expressions as catch all when no other matches apply, e.g.

    // Catch all - serve the index page
    routeMatcher.getWithRegEx('.*', function(req) {
      req.response.sendFile("route_match/index.html");
    });    

# WebSockets

[WebSockets](http://en.wikipedia.org/wiki/WebSocket) are a feature of HTML 5 that allows a full duplex socket-like connection between HTTP servers and HTTP clients (typically browsers).

## WebSockets on the server

To use WebSockets on the server you create an HTTP server as normal, but instead of setting a `requestHandler` you set a `websocketHandler` on the server.

    var server = new vertx.HttpServer();

    server.websocketHandler(function(websocket) {
      
      // A WebSocket has connected!
      
    }).listen(8080, 'localhost');
    
### Reading from and Writing to WebSockets    
    
The `websocket` instance passed into the handler implements both `ReadStream` and `WriteStream`, so you can read and write data to it in the normal ways. I.e by setting a `dataHandler` and calling the `writeBuffer` method.

See the chapter on `NetSocket` and streams and pumps for more information.

For example, to echo all data received on a WebSocket:

    var server = new vertx.HttpServer();

    server.websocketHandler(function(websocket) {
      
      var p = new Pump(websocket, websocket);
      p.start();
      
    }).listen(8080, 'localhost');
    
The `websocket` instance also has method `writeBinaryFrame` for writing binary data. This has the same effect as calling `writeBuffer`.

Another method `writeTextFrame` also exists for writing text data. This is equivalent to calling 

    websocket.writeBuffer(new vertx.Buffer('some-string'));    

### Rejecting WebSockets

Sometimes you may only want to accept WebSockets which connect at a specific path.

To check the path, you can query the `path` property of the websocket. You can then call the `reject` function to reject the websocket.

    var server = new vertx.HttpServer();

    server.websocketHandler(function(websocket) {
      
      if (websocket.path === '/services/echo') {
        var p = new vertx.Pump(websocket, websocket);
        p.start();  
      } else {
        websocket.reject();
      }        
    }).listen(8080, 'localhost');    
    
## WebSockets on the HTTP client

To use WebSockets from the HTTP client, you create the HTTP client as normal, then call the `connectWebsocket` function, passing in the URI that you wish to connect to at the server, and a handler.

The handler will then get called if the WebSocket successfully connects. If the WebSocket does not connect - perhaps the server rejects it, then any exception handler on the HTTP client will be called.

Here's an example of WebSocket connection;

    var client = new vertx.HttpClient();
    
    client.connectWebsocket('http://localhost:8080/some-uri', function(websocket) {
      
      // WebSocket has connected!
      
    }); 
    
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

Moreover, websockets do not work well with many corporate proxies. This means that's it's not possible to guarantee a websocket connection is going to succeed for every user.

Enter SockJS.

SockJS is a client side JavaScript library and protocol which provides a simple WebSocket-like interface to the client side JavaScript developer irrespective of whether the actual browser or network will allow real WebSockets.

It does this by supporting various different transports between browser and server, and choosing one at runtime according to browser and network capabilities. All this is transparent to you - you are simply presented with the WebSocket-like interface which *just works*.

Please see the [SockJS website](https://github.com/sockjs/sockjs-client) for more information.

## SockJS Server

Vert.x provides a complete server side SockJS implementation.

This enables vert.x to be used for modern, so-called *real-time* (this is the *modern* meaning of *real-time*, not to be confused by the more formal pre-existing definitions of soft and hard real-time systems) web applications that push data to and from rich client-side JavaScript applications, without having to worry about the details of the transport.

To create a SockJS server you simply create a HTTP server as normal and pass it in to the constructor of the SockJS server.

    var httpServer = new vertx.HttpServer();
    
    var sockJSServer = new vertx.SockJSServer(httpServer);
    
Each SockJS server can host multiple *applications*.

Each application is defined by some configuration, and provides a handler which gets called when incoming SockJS connections arrive at the server.     

For example, to create a SockJS echo application:

    var httpServer = new vertx.HttpServer();
    
    var sockJSServer = new vertx.SockJSServer(httpServer);
    
    var config = { prefix: '/echo' };
    
    sockJSServer.installApp(config, function(sock) {
    
        var p = new.vertx.Pump(sock, sock);
        
        p.start();
    });
    
    httpServer.listen(8080);
    
The configuration can take the following fields:

* `prefix`: A url prefix for the application. All http requests whose paths begins with selected prefix will be handled by the application. This property is mandatory.
* `insert_JSESSIONID`: Some hosting providers enable sticky sessions only to requests that have JSESSIONID cookie set. This setting controls if the server should set this cookie to a dummy value. By default setting JSESSIONID cookie is enabled. More sophisticated beaviour can be achieved by supplying a function.
* `session_timeout`: The server sends a `close` event when a client receiving connection have not been seen for a while. This delay is configured by this setting. By default the `close` event will be emitted when a receiving connection wasn't seen for 5 seconds.
* `heartbeat_period`: In order to keep proxies and load balancers from closing long running http requests we need to pretend that the connecion is active and send a heartbeat packet once in a while. This setting controlls how often this is done. By default a heartbeat packet is sent every 25 seconds.
* `max_bytes_streaming`: Most streaming transports save responses on the client side and don't free memory used by delivered messages. Such transports need to be garbage-collected once in a while. `max_bytes_streaming` sets a minimum number of bytes that can be send over a single http streaming request before it will be closed. After that client needs to open new request. Setting this value to one effectively disables streaming and will make streaming transports to behave like polling transports. The default value is 128K.    
* `library_url`: Transports which don't support cross-domain communication natively ('eventsource' to name one) use an iframe trick. A simple page is served from the SockJS server (using its foreign domain) and is placed in an invisible iframe. Code run from this iframe doesn't need to worry about cross-domain issues, as it's being run from domain local to the SockJS server. This iframe also does need to load SockJS javascript client library, and this option lets you specify its url (if you're unsure, point it to the latest minified SockJS client release, this is the default). The default value is `http://cdn.sockjs.org/sockjs-0.1.min.js`

## Reading and writing data from a SockJS server

The object passed into the SockJS handler implements `ReadStream` and `WriteStream` much like `NetSocket` or `WebSocket`. You can therefore use the standard API for reading and writing to the SockJS socket or using it in pumps.

See the chapter on Streams and Pumps for more information.

    var httpServer = new vertx.HttpServer();
    
    var sockJSServer = new vertx.SockJSServer(httpServer);
    
    var config = { prefix: '/echo' };
    
    sockJSServer.installApp(config, function(sock) {
    
        sock.dataHandler(function(buff) {
            sock.writeBuffer(buff);
        });
    });
    
    httpServer.listen(8080);
    
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
    
# SockJS - EventBus Bridge

## Setting up the Bridge

By connecting up SockJS and the vert.x event bus we create a distributed event bus which not only spans multiple vert.x instances on the server side, but can also include client side JavaScript running in browsers.

We can therefore create a huge distributed bus encompassing many browsers and servers. The browsers don't have to be connected to the same server as long as the servers are connected.

On the server side we have already discussed the event bus API.

We also provide a client side JavaScript library called `vertxbus.js` which provides the same event bus API, but on the client side.

This library internally uses SockJS to send and receive data to a SockJS vert.x server called the SockJS bridge. It's the bridge's responsibility to bridge data between SockJS sockets and the event bus on the server side.

Creating a Sock JS bridge is simple. You just create an instance of `vertx.SockJSBridge` as shown in the following example.

You will also need to secure the bridge (see below).

The following example creates and starts a SockJS bridge which will bridge any events sent to the path `eventbus` on to the server side event bus.

    var server = new vertx.HttpServer();

    new vertx.SockJSBridge(server, {prefix : '/eventbus'}, [] );
    
    server.listen(8080);
    
The SockJS bridge currently only works with JSON event bus messages.    

## Using the Event Bus from client side JavaScript

Once you've set up a bridge, you can use the event bus from the client side as follows:

In your web page, you need to load the script `vertxbus.js`, then you can access the vert.x event bus API. Here's an example:

    <script type='text/javascript' src='vertxbus.js'></script>
    
    <script>

        var eb = new vertx.EventBus('http://localhost:8080/eventbus'); 
        
        eb.registerHandler('some-address', function(message) {
        
            console.log('received a message: ' + JSON.stringify(message);
            
        });   
        
        eb.send('some-address', {name: 'tim', age: 587});
    
    </script>
    
You can now communicate seamlessly between different browsers and server side components using the event bus. 

You can find `vertxbus.js` in the `client` directory of the vert.x distribution.

The first thing the example does is to create a instance of the event bus

    var eb = new vertx.EventBus('http://localhost:8080/eventbus'); 
    
The parameter to the constructor is the URI where to connect to the event bus. Since we create our bridge with the prefix `eventbus` we will connect there.

The client side event bus API for registering and unregistering handlers and for sending messages is exactly the same as the server side one. Please consult the chapter on the event bus for full information.    
        
## Securing the Bridge

If you started a bridge like in the above example without securing it, and attempted to send messages to it from the client side you'd find that the messages mysteriously disappeared. What happened to them?

For most applications you probably don't want client side JavaScript being able to send just any message to any verticle on the server side or to all other browsers.

For example, you may have a persistor verticle on the event bus which allows data to be accessed or deleted. We don't want badly behaved or malicious clients being able to delete all the data in your database!

To deal with this, a SockJS bridge will, by default refuse to forward any messages from the client side. It's up to you to tell the bridge what messages are ok for it to pass through.

In other words the bridge acts like a kind of firewall which has a default *deny-all* policy.

Configuring the bridge to tell it what messages it should pass through is easy. You pass in an array of JSON objects that represent *matches*, as the final argument in the constructor of `vertx.SockJSBridge`.

Each match has two fields:

1. `address`. This represents the address the message is being sent to. If you want to filter messages based on address you will use this field.
2. `match`. This allows you to filter messages based on their structure. Any fields in the match must exist in the message with the same values for them to be passed. This currently only works with JSON messages.

When a message arrives from the client, the bridge will look through the available permitted entries.

* If an address has been specified then the address must match with the address in the message for it to be considered matched.

* If a match has been specified, then also the structure of the message must match.

Here is an example:

    var server = new vertx.HttpServer();

    new vertx.SockJSBridge(server, {prefix : '/eventbus'},
      [
        // Let through any messages sent to 'demo.orderMgr'
        {
          address : 'demo.orderMgr'
        },
        // Allow calls to the address 'demo.persistor' as long as the messages
        // have an action field with value 'find' and a collection field with value
        // 'albums'
        {
          address : 'demo.persistor',
          match : {
            action : 'find',
            collection : 'albums'
          }
        },
        // Allow through any message with a field `wibble` with value `foo`.
        {
          match : {
            wibble: 'foo'
          }
        }
      ]);


    server.listen(8080);
    
To let all messages through you can specify an array with a single empty JSON object which will match all messages.

     new vertx.SockJSBridge(server, {prefix : '/eventbus'}, [{}]);
     
**Be very careful!**
    
# File System

Vert.x lets you manipulate files on the file system. File system operations are asynchronous and take a handler function as the last argument. This function will be called when the operation is complete, or an error has occurred.
The first argument passed into the callback is an exception, if an error occurred. This will be `null` if the operation completed successfully. If the operation returns a result that will be passed in the second argument to the handler.

# Synchronous forms

For convenience, we also provide synchronous forms of most operations. It's highly recommended the asynchronous forms are always used for real applications.

The synchronous form does not take a handler as an argument and returns its results directly. The name of the synchronous function is the same as the name as the asynchronous form with `Sync` appended.

## copy

Copies a file.

This function can be called in two different ways:

* `copy(source, destination, handler)`

Non recursive file copy. `source` is the source file name. `destination` is the destination file name.

Here's an example:

    vertx.FileSystem.copy('foo.dat', 'bar.dat', function(err) {
        if (!err) {
            log.info('Copy was successful');
        }
    });


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

This function can be called in two different ways:

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

TODO these are currently returning Java dates - need to wrap them.

* `creationTime`. Date of file creation.
* `lastAccessTime`. Date of last file access.
* `lastModifiedTime`. Date file was last modified.
* `isDirectory`. This will have the value `true` if the file is a directory.
* `isRegularFile`. This will have the value `true` if the file is a regular file (not symlink or directory).
* `isSymbolicLink`. This will have the value `true` if the file is a symbolic link.
* `isOther`. This will have the value `true` if the file is another type.

Here's an example:

    vertx.FileSystem.props('some-file.txt', function(err, props) {
        if (err) {
            log.info('Failed to retrieve file props: ' + err);
        } else {
            log.info('File props are:');
            log.info('Last accessed: ' + props.lastAccessTime);
            // etc 
        }
    }); 

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

Reads a symbolic link. I.e returns the path representing the file that the symbolic link specified by `link` points to.

`readSymLink(link, handler)`

`link` is the name of the link to read. An usage example would be:

    vertx.FileSystem.readSymLink('somelink', function(err, res) {
        if (!err) {
            log.info('Link points at ' + res);
        }
    });
  
## delete

Deletes a file or recursively deletes a directory.

This function can be called in two ways:

* `delete(file, handler)`

Deletes a file. `file` is the file name.

* `delete(file, recursive, handler)`

If `recursive` is `true`, it deletes a directory with name `file`, recursively. Otherwise it just deletes a file.

## mkdir

Creates a directory.

This function can be called in three ways:

* `mkdir(dirname, handler)`

Makes a new empty directory with name `dirname`, and default permissions `

* `mkdir(dirname, createParents, handler)`

If `createParents` is `true`, this creates a new directory and creates any of its parents too. Here's an example
    
    vertx.FileSystem.mkdir('a/b/c', true, function(err, res) {
       if (!err) {
         log.info('Directory created ok');
       }
    });
  
* `mkdir(dirname, createParents, perms, handler)`

Like `mkdir(dirname, createParents, handler)`, but also allows permissions for the newly created director(ies) to be specified. `perms` is a Unix style permissions string as explained earlier.

## readDir

Reads a directory. I.e. lists the contents of the directory.

This function can be called in two ways:

* `readDir(dirName)`

Lists the contents of a directory

* `readDir(dirName, filter)`

List only the contents of a directory which match the filter. Here's an example which only lists files with an extension `txt` in a directory.

    vertx.FileSystem.readDir('mydirectory', '*.txt', function(err, res) {
      if (!err) {
        log.info('Directory contains these .txt files');
        for (var i = 0; i < res.length; i++) {
          log.info(res[i]);  
        }
      }
    });
  
## readFile

Read the entire contents of a file in one go. *Be careful if using this with large files since the entire file will be stored in memory at once*.

`readFile(file)`. Where `file` is the file name of the file to read.

The body of the file will be returned as a `Buffer` in the handler.

Here is an example:

    vertx.FileSystem.readFile('myfile.dat', function(err, res) {
        if (!err) {
            log.info('File contains: ' + res.length() + ' bytes');
        }
    });

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

    vertx.FileSystem.exists('some-file.txt', function(err, res) {
        if (!err) {
            log.info('File ' + (res ? 'exists' : 'does not exist'));
        }
    });

## fsProps

Get properties for the file system.

`fsProps(file, handler)`. Where `file` is any file on the file system.

The result is returned in the handler. The result object has the following fields:

* `totalSpace`. Total space on the file system in bytes.
* `unallocatedSpace`. Unallocated space on the file system in bytes.
* `usableSpace`. Usable space on the file system in bytes.

Here is an example:

    vertx.FileSystem.fsProps('mydir', function(err, res) {
        if (!err) {
            log.info('total space: ' + res.totalSpace);
            // etc
        }
    });


## open

Opens an asynchronous file for reading \ writing.

This function can be called in four different ways:

* `open(file, handler)`

Opens a file for reading and writing. `file` is the file name. It creates it if it does not already exist.

* `open(file, openFlags, handler)`

Opens a file using the specified open flags. `file` is the file name. `openFlags` is an integer representing whether to open the flag for reading or writing and whether to create it if it doesn't already exist.

`openFlags` is constructed from a combination of these three constants.

    vertx.FileSystem.OPEN_READ = 1
    vertx.FileSystem.OPEN_WRITE = 2
    vertx.FileSystem.CREATE_NEW = 4
  
For example

    // Open for reading only
    var flags = vertx.FileSystem.OPEN_READ;
  
     // Open for reading and writing
    var flags = vertx.FileSystem.OPEN_READ | vertx.FileSystem.OPEN_WRITE;
  
When the file is opened, an instance of `AsyncFile` is passed into the result handler:

    vertx.FileSystem.open('some-file.dat', vertx.FileSystem.OPEN_READ | vertx.FileSystem.OPEN_WRITE,
        function(err, asyncFile) {
            if (err) {
                log.info('Failed to open file ' + err);
            } else {
                log.info('File opened ok');
                asyncFile.close(); // Close it    
            }
        });    
        
* `open(file, openFlags, flush, handler)`

This is the same as `open(file, openFlags, handler)` but you can also specify whether any file write are flushed immediately to disk (sync'd).

Default is `flush = false`, so writes are just written into the OS cache.

* `open(file, openFlags, flush, perms, handler)`

This is the same as `open(file, openFlags, flush, handler)` but you can also specify the file permissions to give the file if it is created. Permissions is a Unix-style permissions string as explained earlier in the chapter.


## AsyncFile

Instances of `AsyncFile` are returned from calls to `open` and you use them to read from and write to files asynchronously. They allow asynchronous random file access.

AsyncFile can provide instances of `ReadStream` and `WriteStream` via the `getReadStream` and `getWriteStream` functions, so you can pump files to and from other stream objects such as net sockets, http requests and responses, and websockets.

They also allow you to read and write directly to them.

### Random access writes

To use an AsyncFile for random access writing you use the write method.

`write(buffer, position, handler)`.

The first parameter `buffer` is the buffer to write.

The second parameter `position` is an integer position in the file where to write the buffer. If the position is greater or equal to the size of the file, the file will be enlarged to accomodate the offset.

Here is an example of random access writes:

    vertx.FileSystem.open('some-file.dat', function(err, asyncFile) {
            if (err) {
                log.info('Failed to open file ' + err);
            } else {
                // File open, write a buffer 5 times into a file              
                var buff = new vertx.Buffer('foo');
                for (var i = 0; i < 5; i++) {
                    asyncFile.write(buff, buff.length() * i, function(err) {
                        if (err) {
                            log.info('Failed to write ' + err);
                        } else {
                            log.info('Written ok');
                        }
                    });    
                }
            }
        });   

### Random access reads

To use an AsyncFile for random access reads you use the read method.

`read(buffer, offset, position, length, handler)`.

`buffer` is the buffer into which the data will be read.

`offset` is an integer offset into the buffer where the read data will be placed.

`position` is the position in the file where to read data from.

`length` is the number of bytes of data to read

Here's an example of random access reads:

    vertx.FileSystem.open('some-file.dat', function(err, asyncFile) {
        if (err) {
            log.info('Failed to open file ' + err);
        } else {                   
            var buff = new vertx.Buffer(1000);
            for (var i = 0; i < 10; i++) {
                asyncFile.read(buff, i * 100, i * 100, 100, function(err) {
                    if (err) {
                        log.info('Failed to read ' + err);
                    } else {
                        log.info('Read ok');
                    }
                });    
            }
        }
    });   
    
### Flushing data to underlying storage.

If the AsyncFile was not opened with `flush = true`, then you can manually flush any writes from the OS cache by calling the `flush` function.

### Using AsyncFile as `ReadStream` and `WriteStream`

Use the functions `getReadStream` and `getWriteStream` to get read and write streams. You can then use them with a pump to pump data to and from other read and write streams.

Here's an example of pumping data from a file on a client to a HTTP request:

    var client = new vertx.HttpClient().setHost('foo.com');
    
    vertx.FileSystem.open('some-file.dat', function(err, asyncFile) {
        if (err) {
            log.info('Failed to open file ' + err);
        } else {                   
            var request = client.put('/uploads', function(resp) {
                log.info('resp status code ' + resp.statusCode);
            });
            var rs = asyncFile.getReadStream();
            var pump = new vertx.Pump(rs, request);
            pump.start();
            rs.endHandler(function() {
                // File sent, end HTTP requuest
                request.end();
            });
            
        }
    });   
    
### Closing an AsyncFile

To close an AsyncFuile call the `close` function. Closing is asynchronous and if you want to be notified when the close has been completed you can specify a handler function as an argument to `close`.

# Performance Tuning

**TODO**


   
    

    

   


