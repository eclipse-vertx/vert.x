
[TOC]

# Writing Verticles

We previously discussed how a Verticle was the atomic unit in vert.x, and the unit of deployment. We discussed that verticles can be written in several different programming languages and deployed to the same or different servers. Let's look in more detail about how to write a verticle.

Take a look at the simple TCP echo server verticle again.

## JavaScript

The main JavaScript script must call `load('vertx.js')` to load the vertx global which contains the api.

The `load` function is also used if you want to load any other JavaScript scripts you have in your verticle.

The main script is simply run when the verticle is deployed.

The optional function `vertxStop` is called when the verticle is undeployed. This is an optional function that should be used if your verticle needs to do any clean-up, such as shutting down servers or clients or unregistering handlers. 

    load('vertx.js')

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
      new vertx.Pump(sock, sock).start();
    }).listen(1234, 'localhost');

    function vertxStop() {
      server.close();
    }
    
## Ruby

All scripts in your Ruby verticle should `require 'vertx'` in order to load the vert.x api  

The main script is simply run when the verticle is deployed.

The optional method `vertx_stop` is called when the verticle is undeployed. This is an optional function that should be used if your verticle needs to do any clean-up, such as shutting down servers or clients or unregistering handlers.   
    
    require "vertx"
    include Vertx

    @server = NetServer.new.connect_handler { |socket|
      Pump.new(socket, socket).start
    }.listen(1234)

    def vertx_stop
      @server.close
    end

## Java  

Java is not a scripting language so vert.x can't just *execute the class* in order to start the verticle. All Java verticles must implement the interface `org.vertx.java.core.app.VertxApp`. This interface has a method `start()` which is called when the verticle is deployed and a method `stop()` which is called when the verticle is undeployed.

    import org.vertx.java.core.Handler;
    import org.vertx.java.core.app.VertxApp;
    import org.vertx.java.core.net.*;
    import org.vertx.java.core.streams.Pump; 

    public class EchoServer implements VertxApp {

      private NetServer server;

      public void start() {
        server = new NetServer().connectHandler(new Handler<NetSocket>() {
          public void handle(final NetSocket socket) {
            new Pump(socket, socket).start();
          }
        }).listen(1234);
      }

      public void stop() {
        server.close();
      }
    }
    
# Getting Configuration in a Verticle

If JSON configuration has been passed when deploying a verticle from either the command line using `vertx run` or `vertx deploy` and specifying a configuration file, or when deploying programmatically, that configuration is available to the verticle using the `vertx.getConfiguration` function. For example:

    var config = vertx.getConfig();
    
    // Do something with config
    
The config returned is a JSON object.    
   
# Deploying and Undeploying Verticles Programmatically

You can also deploy and undeploy verticles programmatically from inside another verticle. Any verticles deployed programmatically inherit the path of the parent verticle. 


## Deploying a simple verticle

To deploy a verticle programmatically call the function `vertx.deployVerticle`. The return value of `vertx.deployVerticle` is the unique id of the deployment, which can be used later to undeploy the verticle.

To deploy a single instance of a verticle :

    vertx.deployVerticle('my_verticle.js');    
    
## Passing configuration to a verticle programmatically   
  
JSON configuration can be passed to a verticle that is deployed programmatically. Inside the deployed verticle the configuration is accessed with the `vertx.getConfig` function. For example:

    var config = { name: 'foo', age: 234 };
    vertx.deployVerticle('my_verticle.js', config); 
            
Then, in the `my_verticle.js` you can access the config via `vertx.getConfig` as previously explained.
    
## Using a Verticle to co-ordinate loading of an Application

If you have an appplication that is composed of multiple verticles that all need to be started at startup, then you can use another verticle that maintains the applicaton configuration and starts all the other verticles as your application starter. 

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

By default only one instance of the verticle is deployed. If you want to specify more than one instance you can specify the number of instances as follows:

    vertx.deployVerticle('my_verticle.js', null, 10);   
  
The above example would deploy 10 instances.

## Getting Notified when Deployment is complete

Actually deploying the verticle is asynchronous and might not occur until some time after the call to `deployVerticle` has returned. If you want to be notified when the verticle has actually been deployed, you can pass a handler as the final argument to `deployVerticle`:

    vertx.deployVerticle('my_verticle.js', null, 10, function() {
        log.println("It's been deployed!");
    });  
    
## Deploying Worker Verticles

The `vertx.deployVerticle` method deploys standard (non worker) verticles. If you want to deploy worker verticles use the `vertx.deployWorkerVerticle` function. This function takes the same parameters as `vertx.deployVerticle` with the same meanings.

## Undeploying a Verticle

To undeploy a verticle call the `vertx.undeployVerticle` function passing in the deployment id that was returned from the call to `vertx.deployVerticle`

    var deploymentID = vertx.deployVerticle('my_verticle.js');    
    
    vertx.undeployVerticle(deploymentID);

            
# The Event Bus

The event bus is the nervous system of vert.x. It allows verticles to communicate each other irrespective of whether they're in the same vert.x instance, or in a different vert.x instance. It even allows client side JavaScript running in a browser to communicate with verticles. More on that later.

The event bus API is incredibly simple. It basically involves registering handlers, unregistering handlers and sending messages.

First some preliminaries:

## The Theory

### Addressing

Messages are sent on the event bus to an *address*. vert.x doesn't bother with any fancy addressing schemes. In vert.x an address is simply an arbitrary string, although it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.

Some examples of valid addresses are `europe.news.feed1`, `acme.games.pacman`, `sausages`, and `X`. 

### Handlers

You register a handler at an address. The handler will be called when any messages which have been sent to that address have been received. Many different handlers from the same or different verticles can be registered at the same address. A single handler can be registered by the verticle at many different addresses.

When a message is received in a handler, and has been *processed*, the receiver can optionally decide to reply to the message. If they do so, and the message was sent specifying a reply handler, that reply handler will be called.

### Sending messages

You send a message by specifying the address and telling the event bus to send it there. The event bus will then deliver the message to any handlers registered at that address. If multiple vert.x instances are clustered together, the message will be delivered to any matching handlers irrespective of what vert.x instance they reside on.  

The vert.x event bus is therefore an implementation of *Publish-Subscribe Messaging*.

When sending a message you can specify an optional reply handler which will be invoked once the message has reached a handler and the recipient has replied to it.

The vert.x event bus therefore also implements the *Request-Response* messaging pattern.

All messages in the event bus are transient, and in case of failure of all or parts of the event bus, there is every chance the message will be lost. If your application cares about lost messages, you should code your handlers to be idempotent, and your senders to retry after recovery.

Messages that you send on the event bus can be as simple as a string, a number or a boolean. You can also send vert.x buffers [LINK] or JSON Messages [LINK]. If your messages are more than trivial, it's a common convention in vert.x to use JSON messages to communicate between verticles. JSON is easy to create and parse in all the languages that vert.x supports.

## Event Bus API

### Registering and Unregistering Handlers

To set a message handler on the address `test.address`, you do the following:

    var eb = vertx.EventBus;
    
    var myHandler = function(message)) {
      log.println('I received a message ' + message);
    }
    
    eb.registerHandler('test.address', myHandler);
    
It's as simple as that. The handler will then receive any messages sent to that address.

When you register a handler on an address and you're in a cluster it can take some time for the knowledge of that new handler to be propagated across the entire to cluster. If you want to be notified when that has happened you can optionally specify another function to the `registerHandler` function as the third argument. This function will then be called once the information has reached all nodes of the cluster. E.g. :

    eb.registerHandler('test.address', myHandler, function() {
        log.println('Yippee! The handler info has been propagated across the cluster';
    });

To unregister a handler it's just as straightforward. You simply call `unregisterHandler` passing in the address and the handler:

    eb.unregisterHandler('test.address', myHandler);    
    
A single handler can be registered multiple times on the same, or different addresses so in order to identify it uniquely you have to specify both the address and the handler. 

Like with registering, when you unregister a handler and you're in a cluster it can also take some time for the knowledge of that new handler to be propagated across the entire to cluster. If you want to be notified when that has happened you can optionally specify another function to the registerHandler as the third argument. E.g. :

    eb.unregisterHandler('test.address', myHandler, function() {
        log.println('Yippee! The handler unregister has been propagated across the cluster';
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
* vert.x Buffer

Here are some more examples:

Send some numbers:

    eb.send('test.address', 1234);    
    eb.send('test.address', 3.14159);        
    
Send a boolean:

    eb.send('test.address', true);        
    
Send a JSON object:

    var myObj = {
      name: 'Tim'
      address: 'The Moon'
      age: 457    
    }
    eb.send('test.address', myObj); 
    
Null messages can also be sent:

    eb.send('test.address', null);  
    
It's a good convention to have your verticles communicating using JSON.

### Replying to messages

Sometimes after you send a message you want to receive a reply from the recipient. This is known as the *request-response pattern*.

To do this you send a message, specifying a reply handler as the third argument. When the receiver receives the message they are passed a replier function as the second parameter to the handler. When this function is invoked it causes a reply to be sent back to the sender where the reply handler is invoked. An example will make this clear:

The receiver:

    var myHandler = function(message, replier) {
      log.println('I received a message ' + message);
      
      // Do some stuff
      
      // Now reply to it
      
      replier('This is a reply');
    }
    
    eb.registerHandler('test.address', myHandler);
    
The sender:

    eb.send('test.address', 'This is a message', function(reply) {
        log.println('I received a reply ' + reply);
    });
    
It is legal also to send an empty reply or null reply.

## Distributed event bus

To make each vert.x instance on your network participate on the same event bus, start each vert.x instance with the `-cluster` command line switch.

See the chatper on *running vert.x* [LINK] for more information on this.    
      
# Shared Data

Sometimes it makes sense to allow different verticles instances to share data in a safe way. vert.x allows simple *Map* and *Set* data structures to be shared between verticles.

There is a caveat: To prevent issues due to mutable data, vert.x only simple immutable types such as number, boolean and string, or Buffer to be used in shared data. With a Buffer, it is automatically copied when retrieved from the shared data.

Currently data can only be shared between verticles in the *same vert.x instance*. In later versions of vert.x we aim to extend this to allow data to be shared by all vert.x instances in the cluster.

## Shared Maps

To use a shared map to share data between verticles first we get a reference to the map, and then we just use standard `put` and `get` to put and get data from the map:

    var map = vertx.shareddata.getMap('demo.mymap');
    
    map.put('some-key', 'some-value');
    
And then, in a different verticle:

    var map = vertx.shareddata.getMap('demo.mymap');
    
    log.println('value of some-key is ' + map.get('some-key');
    
**TODO** more to do here - API??        
    
## Shared Sets

To use a shared set to share data between verticles first we get a reference to the set.

    var set = vertx.shareddata.getSet('demo.myset');
    
    set.add('some-value');
    
And then, in a different verticle:

    var set = vertx.shareddata.getSet('demo.myset');
    
    /// Hmmmm      
    
    
TODO - a bit more here

API - atomic updates etc      
     
## Periodic Timers

You can also set a timer to fire periodically by using the `setPeriodic` function. There will be an initial delay equal to the period. The return value of `setPeriodic` is a unique timer id (number). This can be later used if the timer needs to be cancelled. The argument passed into the timer event handler is also the unique timer id:

    var id = vertx.setTimer(1000, function(id) {
        log.println('And every second this is printed'); 
    });
    
    log.println('First this is printed');
    
## Cancelling timers

To cancel a periodic timer, call the `cancelTimer` function specifying the timer id. For example:

    var id = vertx.setTimer(1000, function(id) {        
    });
    
    // And immediately cancel it
    
    vertx.cancelTimer(id);
    
Or you can cancel it from inside the event handler. The following example cancels the timer after it has fired 10 times.

    var count = 0;
    
    vertx.setTimer(1000, function(id) {
        log.println('In event handler ' + count); 
        count++;
        if (count === 10) {
            vertx.cancelTimer(id);
        }
    });
    
    log.println('First this is printed');
         
      
    
# Writing TCP Servers and Clients

Creating TCP servers and clients is incredibly easy with vert.x.

## Net Server


### Creating a Net Server

To create a TCP server we simply create an instance of vertx.net.NetServer.

    var server = new vertx.NetServer();
    
### Start the Server Listening    
    
To tell that server to listen on for incoming connections we do:    

    var server = new vertx.NetServer();

    server.listen(1234, 'myhost');
    
The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If it is ommitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


### Getting Notified of Incoming Connections
    
Just having a net server listening creates a working server that you can connect to (try it with telnet!), however it's not very useful since it doesn't do anything with the connections. To be notified when a connection occurs we need call the  `connectHandler` function of the server, passing in a handler:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
      log.println('A client has connected!');
    })  

    server.listen(1234, 'localhost');
    
That's a bit more interesting. Now it displays 'A client has connected!' every time a client connects.   

The return value of the `connectHandler` method is the server itself, so multiple invocations can be chained together. That means we can rewrite the above with:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
      log.println('A client has connected!');
    }).listen(1234, 'localhost');
    
This is a common pattern throughout the vert.x api.    

### Closing a Net Server

To close a net server just call the `close` function.

    server.close();

The close is actually asynchronous and might not complete until some time after the `close` function has returned. If you want to be notified when the actual close has completed then you can pass in a function to the `close` function. This handler will then be called when the close has fully completed.
 
    server.close(function() {
      log.println('The server is now fully closed.');
    });
    
### NetServer Properties

NetServer has a set of properties you can set which affect its behaviour. Firstly there are bunch of properties used to tweak the TCP parameters, in most cases you won't need to set these:

* `setTCPNoDelay(tcpNoDelay)` If `tcpNoDelay` is true then [Nagle's Algorithm](http://en.wikipedia.org/wiki/Nagle's_algorithm) is disabled. If false then it is enabled.

* `setSendBufferSize(size)` Sets the TCP send buffer size in bytes.

* `setReceiveBufferSize(size)` Sets the TCP received buffer size in bytes.

* `setTCPKeepAlive(keepAlive)` if `keepAlive` is true then [TCP keep alive](http://en.wikipedia.org/wiki/Keepalive#TCP_keepalive) is enabled, if false it is disabled. 

* `setReuseAddress(reuse)` if `reuse` is true then addresses in TIME_WAIT state can be reused after they have been closed.

* `setSoLinger(linger)`

* `setTrafficClass(trafficClass)`

NetServer has a further set of properties which are used to configure SSL. We'll discuss those later on.

### Handling Data

So far we have seen how to create a NetServer, and accept incoming connections, but not how to do anything interesting with the connections, let's do that now.

When a connection is made, the connect handler is called passing in an instance of `NetSocket`. This is a socket-like interface to the actual connection, and allows you to read and write data as well as various other operations.

#### Reading Data from the Socket

To read data from the socket you need to set the `dataHandler` on the socket. This handler will be called with a `Buffer` [LINK] every time data is received on the socket. You could try the following code and telnet to it to send some data:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
      sock.dataHandler(function(buffer) {
        log.println('I received ' + buffer.length() + ' bytes of data');
      });
      
    }).listen(1234, 'localhost');
    
#### Writing Data to a Socket

To write data to a socket, you invoke the `write` function. This function can be done in a few ways:

With a single buffer:

    var myBuffer = ...
    sock.write(myBuffer);
    
A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    sock.write('hello');    
    
A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.     

    sock.write('hello', 'UTF-16');
    
The `write` function is asynchronous and always returns immediately after the write has been queued. The actual write might occur some time later. If you want to be informed when the actual write has happened you can pass in a function as a final argument. This function will then be invoked when the write has completed:

    sock.write('hello', function() {
        log.println('It has actually been written');
    });

Putting it all together here's an example of a simple TCP echo server which simply writes back (echoes) everything that it receives on the socket:

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
        
            log.println('The socket is now closed');    
        
        });
    });

The closed handler will be called irrespective of whether the close was initiated by the client or server.

### Exception handler

You can set an exception handler on the socket that will be called if an exception occurs:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
        
        sock.exceptionHandler(function() {
        
            log.println('Oops. Something went wrong');    
        
        });
    });

    
### Read and Write Streams

NetSocket also can at as a `ReadStream` and a `WriteStream`. This allows flow control to occur on the connection and the connection data to be pumped to and from other object such as HTTP requests and responses, websockets and asynchronous files.

This will be discussed in depth in the chapter on streams [LINK] 

## Scaling TCP Servers

A verticle instance is strictly single threaded. If I create a simple TCP server and deploy a single instance of it then all the handlers for that server are always executed on the same event loop (thread). This means that if you are running on a server with a lot of cores, and you only have this one instance deployed then you will have at most one core utilised on your server! That's not very good.

To remedy this you can simply deploy more instances of the verticle in the server, e.g.

    vertx deploy echo_server.js -instances 20
    
Would deploy 20 instances of echo_server.js to a locally running vert.x instance. Once you do this you will find the echo server works functionally identically to before, but, as if by magic all your cores on your server can be utilised and more work can be handled.

By this point you might be asking yourself *'Hang on a second, how can you have more than one server listening on the same host and port? Surely you will get port conflicts as soon as they try and deploy more than one instance?'*

*Vert.x does a little magic here*. When you deploy another server on the same host and port as an existing server it doesn't actually try and create a new server listening on the same host/port, instead it internally maintains just a single server, and, as incoming connections arrive it distributes them in a round-robin fashion to any of the connect handlers set by the verticles. Consequently vert.x TCP servers can scale over available cores while each vert.x verticle instance remains strictly single threaded :)

    
## NetClient

A NetClient is used to make TCP connections to servers.

### Creating a Net Client

To create a TCP server we simply create an instance of vertx.net.NetClient.

    var client = new vertx.NetClient();

### Making a Connection

To actually connect to a server you invoke the `connect` method:

    var client = new vertx.NetClient();
    
    client.connect(1234, 'localhost', function(sock) {
        log.println('We have connected');
    });
    
The connect method takes the port number as the first parameter, followed by the hostname or ipaddress of the server. The third parameter is a connect handler. This handler will be called when the connection actually occurs.

The argument passed into the connect handler is an instance of `NetSocket`, exactly the same as what is passed into the server side connect handler. Once given the `NetSocket` you can read and write data from the socket in exactly the same way as you do on the server side.

You can also close it, set the closed handler, set the exception handler and use it as a `ReadStream` or `WriteStream` exactly the same as the server side `NetSocket`.

### Catching exceptions on the Net Client

You can set a connection handler on the NetClient. This will catch any exceptions that occur during connection.

    var client = new vertx.NetClient();
    
    client.exceptionHandler(function(ex) {
      log.println('Cannot connect since the host was made up!');
    });
    
    client.connect(4242, 'host-that-doesnt-exist', function(sock) {
      log.println('this won't get called');
    });


### Configuring Reconnection

A NetClient can be configured to automatically retry connecting to the server in the event that it cannot connect or has lost its connection. This is done by invoking the functions `setReconnectAttempts` and `setReconnectInterval`

    var client = new vertx.NetClient();
    
    client.setReconnectAttempts(1000);
    
    client.setReconnectInterval(500);
    
`ReconnectAttempts` determines how many times the client will try to connect to the server before giving up. A value of `-1` represents an infinite number of times.

`ReconnectInterval` detemines how long, in milliseconds, the client will wait between reconnect attempts.

The default value for `ReconnectAttempts` is `0`. I.e. no reconnection is attempted.

If an exception handler is set on the client, and reconnect attempts is not equal to `0`. Then the exception handler will not be called until the client gives up reconnecting.


### NetClient Properties

Just like NetServer, NetClient also has a set of TCP properties you can set which affect its behaviour. They have the same meaning as those on NetServer.

NetServer also has a further set of properties which are used to configure SSL. We'll discuss those later on.

## SSL Servers

Net Servers can also be configured to work with [Transport Layer Security](http://en.wikipedia.org/wiki/Transport_Layer_Security) (previously known as SSL).

When a Net Server is working as an SSL Server the api of the NetServer and NetSocket is identical compared to when it working with standard sockets. Getting the server to use SSL is just a matter of configuring the Net Server before listen is called.

To enabled ssl the function `setSSL(true)` must be called on the Net Server.

The server must also be configured with a *key store* and an optional *trust store*. These are both *Java keystores* which can be managed using the [keytool](http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/keytool.html) utility which ships with the JDK. keytool allows you to create keystores, and import and export certificates from them.

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
                   .setTrustStorePath('/path/to/your/keystore/server-truststore.jks')
                   .setTrustStorePassword('password')
                   .setClientAuthRequired(true);
    
Making sure that `server-truststore.jks` contains the certificates of any clients who the server trusts. If `clientAuthRequired` is set to `true` and the client cannot provide a certificate, or it provides a certificate that the server does not trust then the connection attempt will not succeed.

## SSL Clients

Net Clients can also be easily configured to use SSL. They have the exact same api when using SSL as when using standard sockets.

To enable SSL on a Net Client the function `setSSL(true)` is called.

If the `setTrustAll(true)` is invoked on the client, then the client will trust all server certificates. The connection will still be encrypted but this mode is vulnerable to 'man in the middle' attacks. I.e. you can't be sure who you are connecting to. Use this with caution. Default value is `false`.

If `setTrustAll(true)` has not been invoked then a client trust store must be configured and should contain the certificates of the servers that the client trusts. The client trust store is just a standard Java key store, the same as the key stores on the server side. The client trustore location is set by using the function `setTrustStorePath` on the Net Client. If a server presents a certificate during connection which is not in the client trust store, the connection attempt will not succeed.

If the server requires that clients authentication is required then the client must present its own certificate to the server when connecting. This certificate should reside in the client key store. Again its just a regular Java key store. The client keystore location is set by using the function `setKeyStorePath` on the Net Client. 

To configure a client to trust all server certificates (dangerous):

    var client = new vertx.NetClient()
                   .setSSL(true)
                   .setTrustAll(true)
    
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
                   .setKeyStorePath('/path/to/keystore/holding/client/cert/client-keystore.jks')
                   .setKeyStorePassword('password');
                     
 

# Flow Control - Streams and Pumps

There are several objects in vert.x that allow data to be read from and written to in the form of Buffers. All operations in the vert.x API are non blocking, calls to write return immediately and writes are internally queued. It's not hard to see that if you write to an object faster than it can actually write the data to its underlying resource then the write queue could grow without bound eventually resulting in exhausting available memory.

To solve this problem a simple flow control capability is provided by some objects in the vert.x API. Any flow control aware object that can be written to is said to implement `ReadStream`, and any flow control object that can be read from is said to implement `WriteStream`.

Let's take an example where we want to read from one `ReadStream` and write the data to a `WriteStream`. An example would be reading from a `NetSocket` and writing to an `AsyncFile` on disk. A naive way to do this would be to directly take the data read and immediately write it to the NetSocket, as follows:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
      var asyncFile = vertx.FileSystem.open('some_file.dat');
    
      sock.dataHandler(function(buffer) {
      
        // Stream all data directly to the disk file:
      
        asyncFile.write(buffer); 
      });
      
    }).listen(1234, 'localhost');
    
There's a problem with the above example: If data is read from the socket faster than it can be written to the underlying disk file, it will build up in the write queue of the AsyncFile, eventually running out of RAM.

It just so happens that `AsyncFile` implements `WriteStream`, so we can check if the `WriteStream` is full before writing to it:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
      var asyncFile = vertx.FileSystem.open('some_file.dat');
    
      sock.dataHandler(function(buffer) {
      
        // Stream all data directly to the disk file:
        if (!asyncFile.writeQueueFull()) {      
            asyncFile.writeBuffer(buffer); 
        }
      });
      
    }).listen(1234, 'localhost');
    
This example won't run out of RAM but we'll end up losing data if the write queue gets full. What we really want to do is pause the `NetSocket` when the `AsyncFile` `WriteQueue` is full. Let's do that:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
      var asyncFile = vertx.FileSystem.open('some_file.dat');
    
      sock.dataHandler(function(buffer) {
      
        if (!asyncFile.writeQueueFull()) {      
            asyncFile.writeBuffer(buffer); 
        } else {
           sock.pause();
        }
      });
      
    }).listen(1234, 'localhost');

We're almost there, but not quite. The `NetSocket` now gets paused when the file is full, but we also need to *unpause* it when the file write queue has processed its backlog:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
      var asyncFile = vertx.FileSystem.open('some_file.dat');
    
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
      
    }).listen(1234, 'localhost');

And there we have it. The `drainHandler` event handler will get called when the write queue is ready to accept more data, this resumes the `NetSocket` which allows it to read more data.

The above api usage pattern is common when writing vert.x applications, so we provide a helper class called `Pump` which does all this hard work for you. You just feed it that `ReadStream` and the `WriteStream` and it tell it to start:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
      var asyncFile = vertx.FileSystem.open('some_file.dat');
    
      var pump = new vertx.Pump(sock, asyncFile);
      
      pump.start();
      
    }).listen(1234, 'localhost');
    
The above does exactly the same thing as the previous example.

Let's look at the methods on `ReadStream` and `WriteStream` in more detail:

## ReadStream

`ReadStream` is implemented by `AsyncFile`, `HttpClientResponse`, `HttpServerRequest`, `WebSocket`, `NetSocket` and `SockJSSocket`.

Functions:

* `dataHandler(handler)`: set a handler which will receive data from the `ReadStream`. As data arrives the handler will be passed a Buffer.
* `pause()`: pause the handler. When paused no data will be received in the `dataHandler`.
* `resume()`: resume the handler. The handler will be called if any data arrives.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `ReadStream`.
* `endHandler(handler)`: Will be called when end of stream is reached. This might be when EOF is reached if the `ReadStream` represents a file, or when end of request is reached if its an HTTP request, or when the connection is closed if its a TCP socket.

## WriteStream

`WriteStream` is implemented by `AsyncFile`, `HttpClientRequest`, `HttpServerResponse`, `WebSocket`, `NetSocket` and `SockJSSocket`

Functions:

* `writeBuffer(buffer)`: write a Buffer to the `WriteStream`. This method will never block. Writes are queued internally and asynchronously written to the underlying resource.
* `setWriteQueueMaxSize(size)`: set the number of bytes at which the write queue is considered *full*, and the function `writeQueueFull()` returns `true`. Note that, even if the write queue is considered full, if `writeBuffer` is called the data will still be accepted and queued.
* `writeQueueFull()`: returns `true` if the write queue is considered full.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `ReadStream`.
* `endHandler(handler)`: Will be called when end of stream is reached. This might be when EOF is reached if the `ReadStream` represents a file, or when end of request is reached if its an HTTP request, or when the connection is closed if its a TCP socket.

## Pump

Instances of `Pump` have the following methods:

* `start()`. The start the pump. When the pump is not started, the `ReadStream` is paused.
* `stop(). Stops the pump. Resumes the `ReadStream`.
* `setWriteQueueMaxSize()`. This has the same meaning as `setWriteQueueMaxSize` on the `WriteStream`.
* `getBytesPumped()`. Returns total number of bytes pumped.

# Writing HTTP Servers and Clients

## Writing HTTP servers

Writing full featured, highly performant and scalable HTTP servers is child's play with vert.x

### Creating an HTTP Server

To create an HTTP server we simply create an instance of vertx.net.HttpServer.

    var server = new vertx.HttpServer();
    
### Start the Server Listening    
    
To tell that server to listen on for incoming requests we do:    

    var server = new vertx.HttpServer();

    server.listen(8080, 'myhost');
    
The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If the hostname is ommitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


### Getting Notified of Incoming Requests
    
To be notified when a request arrives we need call the `requestHandler` function of the server, passing in a handler:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
      log.println('An HTTP request has been received');
    })  

    server.listen(8080, 'localhost');
    
This displays 'An HTTP request has been received!' every time am HTTP request arrives on the server. You can try it by running the verticle and pointing your browser at http://localhost:8080.

Similarly to Net Server, the return value of the `requestHandler` method is the server itself, so multiple invocations can be chained together. That means we can rewrite the above with:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
      log.println('An HTTP request has been received');
    }).listen(8080, 'localhost');
       
### Handling HTTP Requests

So far we have seen how to create an HTTPServer and be notified of requests but not how to do anything interesting with the requests. 

When a request arrives, the request handler is called passing in an instance of `HttpServerRequest`. This object represents the server side HTTP request. It contains functions to get the uri, path, request headers and request parameters. It also contains a `response` property which is a reference to an object that represents the server side HTTP response for the object.

#### Request Method

The request object has a property `method` which is a string representing what HTTP method was requested. Possible values are GET, PUT, POST, DELETE, HEAD, OPTIONS, CONNECT, TRACE, PATCH.

#### Request URI

The request object has a property `uri` which contains the full uri of the request. For example, if the request uri was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.uri` would contain the string `http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz`.

#### Request Path

The request object has a property `path` which contains the path of the request. For example, if the request uri was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.path` would contain the string `/a/b/c/page.html`
   
#### Request Query

The request object has a property `query` which contains the query of the request. For example, if the request uri was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.query` would contain the string `param1=abc&param2=xyz`    
        
#### Request Headers

The request headers are available using the `headers()` function on the request object. The return value of the function is just a JavaScript hash. Here's an example that echoes the headers to the output of the response. Run it and point your browser at http://localhost:8080 to see the headers.

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

Similarly to the headers, the request parameters are available using the `params()` function on the request object. The return value of the function is just a JavaScript hash.     

Request parameters are sent on the request uri, after the path. For example if the uri was:

    http://localhost:8080/page.html?param1=abc&param2=xyz
    
Then the params hash would be the following object:

    { param1: 'abc', param2: 'xyz' }
    
#### Reading Data from the Request Body

Sometimes an HTTP request contains a request body that we want to read. The request body is not passed fully read with the HTTP request since it may be very large and we don't want to have problems with available memory.

Instead you set a `dataHandler` on the request object which gets called as parts of the HTTP request arrive. Here's an example:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
      request.dataHandler(function(buffer) {
        log.println('I received ' + buffer.length() + ' bytes');
      });
      
    }).listen(8080, 'localhost'); 


The request object implements the `ReadStream` interface so you can pump the request body to a `WriteStream`. See the chapter on streams and pump for a detailed explanation. You'll notice this is very similar to how data from NetSocket is read.   

If you wanted to read the entire request body before doing something with it you could do something like the following:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
      var body = new vertx.Buffer();  
    
      request.dataHandler(function(buffer) {
        body.appendBuffer(buffer);
      });
      
      request.endHandler(function() {
        log.println('The total body received was ' + body.length() + ' bytes');
      });
      
    }).listen(8080, 'localhost');   
    
Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the request.

If the HTTP request is using HTTP chunking, then each chunk of the request body will be received in a different call ti the data handler.

Since it's such a common use case to want to read the entire body before processing it, vert.x allows a `bodyHandler` to be set on the request object. The body handler does is called when the *entire* request body has been read. Beware of doing this with very large requests since the entire request body will be stored in memory.

Here's an example using `bodyHandler`:

    var server = new vertx.HttpServer();

    server.requestHandler(function(request) {
    
      request.bodyHandler(function(body) {
        log.println('The total body received was ' + body.length() + ' bytes');
      });
      
    }).listen(8080, 'localhost');  
    
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

To write data to an http response, you invoke the `write` function. This function can be invoked in a few ways:

With a single buffer:

    var myBuffer = ...
    request.response.write(myBuffer);
    
A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.response.write('hello');    
    
A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.     

    request.response.write('hello', 'UTF-16');
    
The `write` function is asynchronous and always returns immediately after the write has been queued. The actual write might occur some time later. If you want to be informed when the actual write has happened you can pass in a function as a final argument. This function will then be invoked when the write has completed:

    request.response.write('hello', function() {
        log.println('It has actually been written');
    });  
    
If you are just writing a single string or Buffer to the HTTP response you can write it and end the response in a single call to the `end` function.   

The first call to `write` results in the response header being being written to the response. Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the response, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry. 
   
#### Ending HTTP responses

Once you have finished with the HTTP response you must call the `end()` function on it.

This function can be invoked in several ways:

With no arguments, the response is simply ended. 

    request.response.end();
    
The function can also be called with a string or Buffer in the same way `write` is called. In this case it's just the same as calling write with a string or Buffer followed by calling `end` with no arguments.

You can also optionally call `end` with a final boolean argument. If this argument is `true` then the underlying connection will be closed when the response has been written, otherwise the underlying connection will be left open.

#### Response headers

Individual response headers can be written using the `putHeader` method. For example:

    request.response.putHeader('Content-Length', '0');    
    
Response headers must all be added before any parts of the response body are written.

If you wish to add several headers in one operation, just call `putHeaders` with a hash of the headers:

    request.response.putHeaders({ 'Content-Length' : '0', 'Some-Other-Header': 'abc'});
    
#### Chunked HTTP Responses and Trailers

vert.x also supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding). This allows the HTTP response body to be written in chunks, and is normally used when a large response body is being streamed to a client, whose size is not known in advance.

You put the HTTP response into chunked mode as follows:

    req.response.setChunked(true);
    
Default is non-chunked. When in chunked mode, each call to `response.write(...)` will result in a new HTTP chunk being written out.  

When in chunked mode you can also write HTTP response trailers to the response. These are actually written in the final chunk of the response.  

To write an individual trailer use the `putTrailer` method:

    request.response.putTrailer('Some-Trailer', 'some value'); 
    
If you wish to add several trailers in one operation, just call `putTrailers` with a hash of the trailers:

    request.response.putTrailers({ 'Some-Trailer' : 'some value', 'Some-Other-Trailer': 'wibble'});    


### Serving files directly disk

You could stream a file from disk to a HTTP response by opening an `AsyncFile` using the file system and pumping it to the response, or you could load it in one go using the file system and write that to the response.

Alternatively, vert.x provides a feature where you can stream files directly from disk to the response bypassing userspace altogether. This functionality is OS dependent and leverages the `sendfile` operation to tell the kernel to directly serve the file. Using this operation can greatly reduce CPU utilisation.

To do this use the `sendfile` function on the HTTP response, for example here's a simple HTTP web server that serves static files from the local `web` directory:

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

**If you're going to write web servers using vert.x be careful that users can exploit the path to access files outside the directory from which you want to serve them.**

### Pumping Responses

Since the HTTP Response implements `WriteStream` you can pump to it from any `ReadStream`, e.g. an `AsyncFile`, `NetSocket` or `HttpServerRequest`. Here's an example which simply echoes an HttpRequest headers and body back in the HttpResponse. Since it uses a pump for the body, it will work even if the HTTP request body is much larger than can fit in memory at any one time:

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
    
You can set the port and hostname (or ip address) that the client will connect to be using the `setHost` and `setPort` functions:

    var client = new vertx.HttpClient();
    client.setPort(8181);
    client.setHost('foo.com');
    
This, of course can be chained:

    var client = new vertx.HttpClient()
                   .setPort(8181);
                   .setHost('foo.com');
                   
A single http client always connects to the same host and port. If you want to connect to different servers, create more instances of http client.

The default value for hostname is `localhost`, and the default value for port is `80`.  

### Pooling and Keep Alive

By default the HTTP client pools HTTP connections. As you make requests a connection is borrowed from the pool and returned when the HTTP response returns.

If you do not want connections to be pooled you can set keep alive to false on the pool:

    var client = new vertx.HttpClient()
                   .setPort(8181);
                   .setHost('foo.com').
                   .setKeepAlive(false);

In this case a new connection will be created for each HTTP request and closed once the response has returned.

You can set the maximum number of connections that the client will pool as follows:

    var client = new vertx.HttpClient()
                   .setPort(8181);
                   .setHost('foo.com').
                   .setMaxPoolSize(10);
                   
The default value is 1.         

### Closing the client

Once you have finished with an HTTP client, you should close it:

    client.close();             
                         
### Making Requests

To make a request using the client you invoke one the methods named after the HTTP methods. For example, to make a POST request:

    var client = new vertx.HttpClient();
    
    var request = client.post('http://localhost:8080/some-uri', function(resp) {
        log.println('Got a response, status code: ' + resp.statusCode);
    });
    
    request.end();
    
To make a PUT request use the `put` method, to make a GET request use the `get` method, etc.

The general modus operandi is you invoke the appropriate method passing in the request URI as the first parameter, the second parameter is an event handler which will get called when the corresponding response arrives. The response handler is passed the client response object as an argument.

The return value from the appropriate request method is a client request object. You can use this to add headers to the request, and to write to the request body. The request object implements `WriteStream`.

Once you have finished with the request you must call the `end` function.

If you don't know the name of the request method in advance there is a general request method which takes the method name as a parameter:

    var client = new vertx.HttpClient();
    
    var request = client.request('POST', 'http://localhost:8080/some-uri', function(resp) {
        log.println('Got a response, status code: ' + resp.statusCode);
    });
    
    request.end();
    
There is a method called `getNow` which does the same as `get`, but automatically ends the request. This is useful for simple GETs which don't have a request body:

    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-uri', function(resp) {
        log.println('Got a response, status code: ' + resp.statusCode);
    });

With `getNow` there is no return value.

#### Writing to the request body

Writing to the client request body has a very similar API to writing to the server response body.

To write data to an http client request, you invoke the `write` function. This function can be invoked in a few ways:

With a single buffer:

    var myBuffer = ...
    request.write(myBuffer);
    
A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.write('hello');    
    
A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.     

    request.write('hello', 'UTF-16');
    
The `write` function is asynchronous and always returns immediately after the write has been queued. The actual write might occur some time later. If you want to be informed when the actual write has happened you can pass in a function as a final argument. This function will then be invoked when the write has completed:

    request.response.write('hello', function() {
        log.println('It has actually been written');
    });  
    
If you are just writing a single string or Buffer to the HTTP request you can write it and end the request in a single call to the `end` function.   

The first call to `write` results in the request header being being written to the request. Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the request, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry. 


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
    
    var request = client.post('http://localhost:8080/some-uri', function(resp) {
        log.println('Got a response, status code: ' + resp.statusCode);
    });
    
    request.putHeader('Some-Header', 'Some-Value');
    
These can be chained together as per the common vert.x API pattern:

    client.post('http://localhost:8080/some-uri', function(resp) {
        log.println('Got a response, status code: ' + resp.statusCode);
    }).putHeader('Some-Header', 'Some-Value')
      .putHeader('Some-Other-Header', 'Some-Other-Value')
      .end();

If you want to put more than one header at the same time, you can instead use the `putHeaders` function.
  
  client.post('http://localhost:8080/some-uri', function(resp) {
        log.println('Got a response, status code: ' + resp.statusCode);
    }).putHeaders({'Some-Header': 'Some-Value',
                   'Some-Other-Header': 'Some-Other-Value'})
      .end(); 

**[[ TODO putHeaders currently not implemented ]]**


#### HTTP chunked requests

vert.x also supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding) for requests. This allows the HTTP request body to be written in chunks, and is normally used when a large request body is being streamed to the server, whose size is not known in advance.

You put the HTTP request into chunked mode as follows:

    request.setChunked(true);
    
Default is non-chunked. When in chunked mode, each call to `request.write(...)` will result in a new HTTP chunk being written out.  

### HTTP Client Responses

Client responses are received as an argument to the response handler that is passed into one of the request methods on the HTTP client.

The response object implements `ReadStream`. To query the status code of the response use the `statusCode` property. The `statusMessage` property contains the status message. For example:

 var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-uri', function(resp) {
      log.println('server returned status code: ' + resp.statusCode);   
      log.println('server returned status message: ' + resp.statusMessage);   
    });

#### Reading Data from the Response Body

The api for reading a client http response body is very similar to the api for read a server http request body.

Sometimes an HTTP response contains a request body that we want to read. The response body is not passed fully read with the HTTP response since it may be very large and we don't want to have problems with available memory.

Instead you set a `dataHandler` on the response object which gets called as parts of the HTTP response arrive. Here's an example:


    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-uri', function(resp) {
      resp.dataHandler(function(buffer) {
        log.println('I received ' + buffer.length() + ' bytes');
      });    
    });

The response object implements the `ReadStream` interface so you can pump the response body to a `WriteStream`. See the chapter on streams and pump for a detailed explanation. 

If you wanted to read the entire response body before doing something with it you could do something like the following:

    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-uri', function(resp) {
      
      var body = new vertx.Buffer();  
    
      resp.dataHandler(function(buffer) {
        body.appendBuffer(buffer);
      });
      
      resp.endHandler(function() {
        log.println('The total body received was ' + body.length() + ' bytes');
      });
      
    });
    
Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the response.

If the HTTP response is using HTTP chunking, then each chunk of the response body will be received in a different call to the data handler.

Since it's such a common use case to want to read the entire body before processing it, vert.x allows a `bodyHandler` to be set on the response object. The body handler is called when the *entire* response body has been read. Beware of doing this with very large responses since the entire response body will be stored in memory.

Here's an example using `bodyHandler`:

    var client = new vertx.HttpClient();
    
    client.getNow('http://localhost:8080/some-uri', function(resp) {
      
      resp.bodyHandler(function() {
        log.println('The total body received was ' + body.length() + ' bytes');
      });
      
    }); 
    
#### Pumping Responses

Like several other objects in vert.x, the HTTP client request implements `WriteStream` you can pump to it from any `ReadStream`, e.g. an `AsyncFile`, `NetSocket` or `HttpServerRequest`. Here's a very simple proxy server which forwards a request to another server and forwards the response back to the client. It uses two pumps - one to pump the server request to the client request, and another to pump the client response back to the server response. Since it uses pumps it will work even if the HTTP request body is much larger than can fit in memory at any one time:

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

According to the [HTTP 1.1 specification] (http://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html) a client can set a header `Expect: 100-Continue` and send the request header before sending the rest of the request body. The server can then respond with an interim response status `Status: 100 (Continue)` to signify the client is ok to send the rest of the body. The idea here is it allows the server to reject the request before large amounts of data has already been sent, which would be a waste of bandwidth.

The vert.x allows you to set a continue handler on the client request object. This will be called if the server sends back a `Status: 100 (Continue)` response to signify it is ok to send the rest of the request. This is used in conjunction with the `sendHead` function to send the head of the request.

An example will illustrate this:

    var client = new vertx.HttpClient();
    
    var request = client.put('http://localhost:8080/some-uri', function(resp) {
      
      resp.bodyHandler(function(resp) {
        log.println('Got a response ' + resp.statusCode);
      });
      
    });     
    
    request.putHeader('Expect', '100-Continue');
    
    request.continueHandler(function() {
        // OK to send rest of body
        
        request.write('Some data').end();
    });
    
    request.sendHead();

## HTTPS Servers

HTTP Servers can also be configured to work with [Transport Layer Security](http://en.wikipedia.org/wiki/Transport_Layer_Security) (previously known as SSL).

When an HTTP Server is working as an HTTPS Server the api of the server is identical compared to when it working with standard HTTP. Getting the server to use HTTPS is just a matter of configuring the HTTP Server before listen is called.

Configuration of an HTTPS server is exactly the same as configuring a Net Server for SSL. Please see SSL server chapter for detailed instructions.

## HTTPS Clients

HTTP Clients can also be easily configured to use HTTPS.

Configuration of an HTTPS client is exactly the same as configuring a Net Client for SSL. Please see SSL client chapter for detailed instructions. 

## Scaling HTTP servers

Scaling an HTTP server over multiple cores is as simple as deploying more instances of the verticle. For example:

    vertx deploy http_server.js -instances 20
    
The scaling works in the same way as scaling a Net Server. Please see the section on scaling Net Servers for a detailed explanation [LINK].   

# Routing HTTP requests with Pattern Matching

With vert.x you can also route HTTP requests to different handlers based on pattern matching on the request path. It also enables you to extract values from the path and use them as parameters in the request. This is particularly useful when developing REST-style web applications.

To do this you simply create an instance of `vertx.RouteMatcher` and use it as handler in an HTTP server. See the chapter on HTTP servers for more information on setting HTTP handlers.

    var server = new vertx.HttpServer();
    
    var routeMatcher = new vertx.RouteMatcher();
        
    server.requestHandler(routeMatcher).listen(8080, 'localhost');
    
## Routing with Static Paths    
    
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
    
Corresponding methods exist for each HTTP method - get, post, put, delete, head, etc.

The handler specified to the method is just a normal HTTP server request handler, the same as you would supply to the `requestHandler` method of the HTTP server.

## Extracting parameters from the path

If you want to extract parameters from the path, you can do this too, by using the `:` (colon) character to denote the name of a parameter. For example:

    var server = new vertx.HttpServer();
    
    var routeMatcher = new vertx.RouteMatcher();
    
    routeMatcher.put('/:blogname/:post', function(req) {        
        var blogName = req.params().blogname;
        var post = req.params().post;
        // Do something
        req.response.end();
    });
    
    server.requestHandler(routeMatcher).listen(8080, 'localhost');
    
Any params extracted by pattern matching are added to the map of request parameters.

In the above example, a PUT request to `/myblog/post1` would result in the variable `blogName` getting the value `myblog` and the variable `post` getting the value `post`.

## Extracting params using Regular Expressions

Regular Expressions can also be used to extract more complex matches. In this case capture groups can be used to capture any parameters. Since the capture groups are not named they are added to the request with names `param0`, `param1`, `param2`, etc.

    var server = new vertx.HttpServer();
    
    var routeMatcher = new vertx.RouteMatcher();
    
    routeMatcher.putWithRegEx('\\/([^\\/]+)\\/([^\\/]+)', function(req) {        
        var first = req.params().param0
        var second = req.params().param1;
        // Do something
        req.response.end();
    });    
    
## Catch all

**[TODO]* finish this off

    // Catch all - serve the index page
    routeMatcher.get('.*', function(req) {
      req.response.sendFile("route_match/index.html");
    });    

# WebSockets

[WebSockets](http://en.wikipedia.org/wiki/WebSocket) are a feature of HTML 5 that allows a full duplex socket-like connection between HTTP servers and HTTP clients (typically browsers).

## WebSockets on the server

To use WebSockets on the server you create an HTTP server as normal [LINK], but instead of setting a `requestHandler` you set a `websocketHandler` on the server.

    var server = new vertx.HttpServer();

    server.websocketHandler(function(websocket) {
      
      // A WebSocket has connected!
      
    }).listen(8080, 'localhost');
    
The `websocket` instance passed into the handler implements both `ReadStream` and `WriteStream`, and has a very similar interface to `NetSocket`, so you can write and write data to it in the normal ways. See chapter on streams [LINK] for more information.

For example, to echo anything received on a WebSocket:

    var server = new vertx.HttpServer();

    server.websocketHandler(function(websocket) {
      
      var p = new Pump(websocket, websocket);
      p.start();
      
    }).listen(8080, 'localhost');


Sometimes you may only want to accept WebSockets which connect at a specific path, to check the path, you can query the `path` property of the websocket. You can then call the `reject` function to reject the websocket.

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

To use WebSockets from the HTTP client, you create the HTTP client as normal, then call the `connectWebsocket` function, passing in the uri that you want to connect at, and a handler. The handler will then get called if the WebSocket successfully connects. If the WebSocket does not connect - perhaps the server rejects it, then any exception handler on the HTTP client will be called.

Here's an example of WebSocket connection;

    var client = new vertx.HttpClient();
    
    client.connectWebsocket('http://localhost:8080/some-uri', function(websocket) {
      
      // WebSocket has connected!
      
    }); 
    
Again, the client side WebSocket implements `ReadStream` and `WriteStream`, so you can read and write to it in the same way as any other stream object. 

WebSocket also implements the convenience method `writeTextFrame` for writing text frames, and `writeBinaryFrame` for writing binary frames.   

## WebSockets in the browser


To use WebSockets from a compliant browser, you use the standard WebSocket api. Here's some example client side JavaScript which uses a WebSocket. 

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
    
# SockJS

WebSockets are a new technology, and many users are still using browsers that do not support them, or which support older versions. Moreover, websockets do not work well with many corporate proxies. This means that's it's not possible to guarantee a websocket connection is going to succeed for every user.

Enter SockJS. SockJS is a client side JavaScript library and protocol which provides a simple WebSocket-like interface to you the developer irrespective of whether the actual browser or network will allow real WebSockets.

It does this by supporting various different transports between browser and server, and choosing one at runtime according to browser and network capabilities. All this is transparent to you - you are simply presented with the WebSocket-like interface which *just works*.

Please see the [SockJS website](https://github.com/sockjs/sockjs-client) for more information.

## SockJS Server

vert.x provides a complete server side SockJS implementationl, enabling vert.x to be used for modern *real-time* web applications that push data to and from rich client-side JavaScript applications, without having to worry about the details of the transport.

To create a SockJS server you simply create a HTTP server as normal [LINK] and pass it in to the constructor of the SockJS server.

    var httpServer = new vertx.HttpServer();
    
    var sockJSServer = new vertx.SockJSServer(httpServer);
    
Each SockJS server can host multiple *applications*. Each application is defined by some configuration, and provides a handler which gets called when incoming SockJS connections arrive at the server.     

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

* prefix: A url prefix for the application. All http requests which paths begins with selected prefix will be handled by the application. This property is mandatory.
* insert_JSESSIONID: Some hosting providers enable sticky sessions only to requests that have JSESSIONID cookie set. This setting controls if the server should set this cookie to a dummy value. By default setting JSESSIONID cookie is enabled. More sophisticated beaviour can be achieved by supplying a function.
* session_timeout: The server sends a `close` event when a client receiving connection have not been seen for a while. This delay is configured by this setting. By default the `close` event will be emitted when a receiving connection wasn't seen for 5 seconds.
* heartbeat_period: In order to keep proxies and load balancers from closing long running http requests we need to pretend that the connecion is active and send a heartbeat packet once in a while. This setting controlls how often this is done. By default a heartbeat packet is sent every 25 seconds.
* max_bytes_streaming: Most streaming transports save responses on the client side and don't free memory used by delivered messages. Such transports need to be garbage-collected once in a while. `max_bytes_streaming` sets a minimum number of bytes that can be send over a single http streaming request before it will be closed. After that client needs to open new request. Setting this value to one effectively disables streaming and will make streaming transports to behave like polling transports. The default value is 128K.    
* library_url: Transports which don't support cross-domain communication natively ('eventsource' to name one) use an iframe trick. A simple page is served from the SockJS server (using its foreign domain) and is placed in an invisible iframe. Code run from this iframe doesn't need to worry about cross-domain issues, as it's being run from domain local to the SockJS server. This iframe also does need to load SockJS javascript client library, and this option lets you specify its url (if you're unsure, point it to the latest minified SockJS client release, this is the default). The default value is `http://cdn.sockjs.org/sockjs-0.1.min.js`

## Reading and writing data from a SockJS server

The object passed into the SockJS handler implements `ReadStream` and `WriteStream` much like `NetSocket` or `WebSocket`. You can therefore use the standard api for reading and writing to the SockJS socket or using it in pumps.

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
    
# SockJS -- EventBus Bridge

## Setting up the Bridge

By connecting up SockJS and the vert.x Event Bus we can create a distributed event bus which not only spans multiple vert.x instances on the server side, but can also include client side JavaScript running in browsers. We can therefore create a huge distributed event space encompassing many browsers and servers. The browsers don't have to be connected to the same server as long as the servers are connected.

On the server side we have already discussed the event bus api. We also provide a client side JavaScript library called `vertxbus.js` which provides the same event bus API on the client side. This library internally uses SockJS to send and receive data from a SockJS vert.x server called the SockJS Bridge. It's the bridge's responsibility to bridge data between SockJS sockets and the event bus.

Creating a Sock JS bridge is simple. You just create a SockJS Server as described in the last chapter, and install an app with a handler which is a `SockJSBridgeHandler`. The following example creates and starts a SockJS bridge which will bridge any events sent to path `eventbus` on to the event bus.

    var server = new vertx.HttpServer();

    var sockJSServer = new vertx.SockJSServer(server);
    
    var handler = new vertx.SockJSBridgeHandler();
    
    sockJSServer.installApp({prefix : '/eventbus'}, handler);
    
    server.listen(8080);
    
The SockJS bridge currently only works with JSON event bus messages.    
    
## Securing the Bridge

Actually, if you started a server like in the previous example and attempted to send messages to it from the client side you'd find that the messages mysteriously disappeared. What happened to them?

For most applications you probably don't want client side JavaScript being able to send just any message to any component on the server side or in other browsers. For example, you may have a persistor component on the event bus which allows data to be accessed or deleted. We don't want badly behaved or malicious clients being able to delete all the data in your database!

To deal with this, by default, a SockJS bridge will refuse to forward any messages from the client side. It's up to you to tell the bridge what messages are ok for it to pass through. In other words the bridge acts like a kind of firewall.

Configuring the bridge to tell it what messages it should pass through is easy. You just call the function `addPermitted` on the bridge handler and pass in any number of JSON objects that represent matches.

Each match has two fields:

1. `address`. This represents the address the message is being sent to. If you want to filter messages based on destination you will use this field.
2. `match`. This allows you to filter messages based on their stricture. Any fields in the match must exist in the message with the same values for them to be passed. This currently only works with JSON messages.

When a message arrives from the client, the bridge will look through the available permitted entries. If an address has been specified then the address must match with the address in the message for it to be considered matched. If a match has been specified, then also the structure of the message must match.

Here is an example:

    var server = new vertx.HttpServer();

    var sockJSServer = new vertx.SockJSServer(server);
    
    var handler = new vertx.SockJSBridgeHandler();

    handler.addPermitted(
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
     
    );
    
    sockJSServer.installApp({prefix : '/eventbus'}, handler);
    
    server.listen(8080);
    
To let all messages through you can add an empty permitted entry:

     handler.addPermitted({});
     
**Be very careful!**
    
# Using the Event Bus from client-side JavaScript    
    
To use the event bus from the client side you need to load the script `vertxbus.js`, and do something like:

    <script type='text/javascript' src='vertxbus.js'></script>
    
    <script>

        var eb = new vertx.EventBus('http://localhost:8080/eventbus'); 
        
        eb.registerHandler('some-address', function(message) {
        
            console.log('received a message: ' + JSON.stringify(message);
            
        });   
        
        eb.send('some-address', {name: 'tim', age: 587});
    
    </script>
    
You can now communicate seamlessly between different browsers and server side components using the event bus.    
    
For a full description of the event bus API, please see the chapter on Event Bus [LINK].    
    
# Delayed and Periodic Tasks

It's very common to want to perform an action after a delay, or periodically.

In standard verticles you can't just call `Thread.sleep` if you want to introduce a delay, since that will block the event loop thread. Instead you use vert.x timers. Timers can be *one-shot* or *periodic*. We'll discuss both

## One-shot Timers

A one shot timer calls a supplied event handler after a certain delay, expressed in milliseconds. 

To set a timer to fire once you use the `vertx.setTimer` function passing in the delay and the handler

    vertx.setTimer(1000, function() {
        log.println('And one second later this is printed'); 
    });
    
    log.println('First this is printed');
    

   


