# User Manual  
   
## Introduction 
   
### What is vert.x? 
      
vert.x is a framework for creating massively scalable, concurrent, real-time, web-enabled applications.  

Some key features:

* Polyglot. Write your application in JavaScript, Java or Ruby. It's up to you. Or mix and match several programming languages in a single /Users/timfox/projects/vert.x-ghpages/new/manual.mdapplication.

* No more worrying about concurrency. vert.x allows you to write all your code as single threaded, freeing you from the hassle of multi-threaded programming.

* An asynchronous, event based API so your applications can scale seamlessly across many cores with just a few threads.

* Distributed event bus that spans the client and server side so your applications components can communicate incredibly easily.


## Concepts in vert.x

In this section I'd like to give an overview of what the different conceptual part of vert.x are and how they hang together. Many of this concepts will be discussed in more depth later on in this manual.

### Verticle

The atomic unit in vert.x is called a *verticle* (Like a particle, but in vert.x). Verticles can be written in JavaScript, Ruby or Java. A verticle is defined by having a *main* which is just the script (or class in the case of Java) to run to start the verticle.

Your application may contain just one verticle, or it could consist of a whole set of verticles communicating with each other using the event bus.

Verticles run inside *vert.x server instance*. 

**TODO details on how to write a verticle in each language and vertxStop()**

### Vert.x Instances

Verticles run inside vert.x instances. A single vert.x instance is basically an operating system process running a JVM. There can be many verticle instances running inside a single vert.x instance at any time. vert.x makes sure each vert.x instance is isolated by giving it its own classloader so they can't interact by sharing static members, global variables or other means.

There can be many vert.x instances running on the same host, or on different hosts on the network at the same time. The instances can be configured to cluster with each other forming a distributed event bus over which verticle instances can communicate.

### Polyglot

We want you to be able to develop your verticles in a choice of programming languages. Never have developers had such a choice of great languages, and we want that to be reflected in the languages we support. vert.x allows you to mix and match verticles written in different languages in the same application. Currently we support JavaScript, Ruby and Java, but we aim to support Groovy, Clojure, Scala and Python going ahead.

### Concurrency

The vert.x instance guarantees that a particular verticle instance is always executed by the exact same thread. This gives you a huge advantage as a developer, since you can program all your code as single threaded. Well, that won't be a big deal to you if you are coming from JavaScript where everything is single threaded, but if you're used to multi-threaded programming in Java, Scala, or even Ruby, this may come as a huge relief!

Gone are the days of worrying about race conditions, locks, mutexes, volatile variables and synchronization. 

### Event-based Programming Model

Most things you do in vert.x involve setting event handlers. E.g. to receive data from a TCP socket you set a handler - the handler is then called when data arrives. You also set handlers to receive messages from the event bus, to receive HTTP requests and responses, to be notified when a connection is closed, or to be notified when a timer fires. There are many examples throughout the vert.x api.

In other words, vert.x provides an event-based programming model. 

Any other operations in vert.x that don't involve handlers, e.g. writing some data to a socket are guaranteed never to block.

*Why is it done this way?*

The answer is: If we want our application to scale with many connections, *we don't have a choice*.

Let's imagine that the vert.x api allowed a blocking read on a TCP socket. When the code in a verticle called that blocking operation and no data arrived for, say, 1 minute, it means that thread cannot do anything else during that time - it can't do work for any other verticle.

For such a blocking model to work and the system to remain responsive, each verticle instance would need to be assigned its own thread. Now consider what happens when we have thousands, 10s of thousands, or 100s of thousands of verticles running. We clearly can't have that many threads - the overhead due to context switching and stack space would be horrendous. It's clear to see such a blocking model just doesn't scale.

The only way to make it scale is have a 100% non blocking api. There are two ways to do this:

* Used an asynchronous, event based API. Let the system call you when events occur. Do not block waiting for things to happen.

* Use some kind of co-routine approach. Co-routines allow you to suspend the execution of a piece of code and come back to a later when an event occurs. However co-routines are not currently supported across the different languages, or versions of languages that we support in vert.x

vert.x currently takes the event-based api approach. As support for coroutines in various languages matures we will consider also supporting a co-routine based approach in the api.

### Event Loops

Internally, the vert.x instance manages a small set of threads, typically matching the number of threads to the available cores on the server. We call these threads event loops, since they basically just loop around (well... they do actually go to sleep if there is nothing to do) seeing if there is any work to do, e.g. reading some data from a socket, or executing a timer.

When a verticle instance is deployed, the server chooses an event loop which will be assigned to that instance. Any subsequent work to be done for that instance will always be dispatched using that thread. Of course, since there are potentially many thousands of verticles running at any one time, a single event loop is assigned to many verticles at the same time.

We call this the *multi-reactor pattern*. It's like the [reactor pattern] (http://en.wikipedia.org/wiki/Reactor_pattern) but there's more than one event loop.

### Message Passing

Verticles can communicate with other verticles running in the same, or different, vert.x instance using the event bus. Each verticle instance is single threaded so in some ways it resembles the [actor model](http://en.wikipedia.org/wiki/Actor_model) popularised by the Erlang programming language. However there are some difference, for example, each verticle can set multiple event handlers, rather than having a single mail-box. You can think of the vert.x model as a superset of the actor model.

By having many verticle instances in a vert.x server instance and allowing message passing allows the system to scale well over available cores without having to allow multi-threaded execution of any verticle code.

### Shared data

Message passing is great, but its not always the best approach to concurrency for certain applications. Consider an application that wishes to provide an in memory web cache. As requests come in to the server, the server looks up the request in the cache and returns it from there if the item is present, if the item is not present it loads it from disk and places it in the cache for the next time.

We want this system to scale across available cores. Modelling this using message passing is problematic. At one end of the scale we could have a single verticle that manages the cache, but this means all requests to the cache will be serialized through a single threaded verticle instance. We could improve things by having multiple instances of the verticle managing different parts of the cache, but it quickly gets ugly and complicated.

Such a use case is better solved by providing a shared map structure that can be accessed directly by different verticle instances in the same vert.x instance. As requests come in, the data can be efficiently looked up in the cache with a single line of code and returned to the user.

It's fashionable these days to deride shared data. But shared data is only dangerous if the data that you share is mutable.

vert.x provides a shared map and shared set facility which allows only *immutable* data to be shared between verticles.


### Blocking and Long Running Operations

Considering vert.x uses only a small number of event loop threads, and considering that vert.x has to dispatch events to potentially thousands of verticles, and remain responsive, it's pretty clear that those threads can't hang around in any verticle event handler for too long - if they do it means that event loop can't service events for any other verticle, and everything can grind to a halt.

What does *hanging around* mean? It means anything that can take more than a few milliseconds of *wall-clock* time. (That's real actual time, not CPU time). That includes things like  the thread sleeping or blocking on a database operation which don't involve much CPU time, but it also involves *busy waits* such as a computationally intensive operation, e.g. factorising prime numbers.

### Worker Verticles

By default verticles event handlers should not take a long time to execute, however there are cases where you can't avoid blocking, or you genuinely have computationally intensive operations to perform.

An example of the former would be calling a third-party blocking database API from a verticle. In this case you don't have control of the client library you are using so you just have to block until you get the result back.

Another example would be a worker verticle which needs to do an intensive calculation like calculating Fibonacci numbers. In such a case the calculation could be done a little at a time, and event handlers set to continue the calculation the next time around the event loop, but this is awkward, and just a little bit silly ;)

For cases like these, vert.x allows you to mark a particular verticle instance as a *worker verticle*. A worker verticle differs from a normal verticle in that it is not assigned a vert.x event loop thread, but instead it executes on a thread from an internal thread pool that vert.x maintains called the *background pool*. 

Worker verticles are never executed concurrently by more than one thread. Worker verticles are also not allowed to use TCP or HTTP clients or servers. Worker verticles normally communicate with other verticles using the vert.x event bus, e.g. receiving work to process.

Worker verticles should be kept to a minimum, since a blocking approach doesn't scale if you want to deal with many concurrent connections. We'll talk more about worker verticles later on.

**TODO more stuff on how to write a worker**

### Code is Config

**TODO**

### Core and BusMods

**TODO**


## Installation

Before you can do anything with vert.x you need to install it, so let's get that out of the way.

### Getting a distro

You can't install it if you haven't got it, so first you need to download it.  

The easiest way is to download a distro from the download page [link]. Alternatively you can build from source. To do that see the instructions on the github wiki.

### Pre-requisites  

* Operating System. vert.x runs out of the box on Linux or OSX. If you are running Windows, the best way to run vert.x is to create a Linux (I recommend Ubuntu) vitrtual machine using your favourite virtualisation software (VMware Workstation, of course!) and run it in that.

* JDK. vert.x requires JDK 1.7.0 or later. You can use the official Oracle distribution or the OpenJDK version. Make sure the JDK bin directory is on your `PATH`.

* Apache Ant. If you want to run the Java examples you will need Apache Ant installed. Otherwise you don't need it.

* Ruby. If you want to deploy Ruby verticles then you will need JRuby 1.6.4 later installed. Please set the `JRUBY_HOME` environment variable to point at the base of the JRuby installation. If you don't intend to deploy Ruby verticles you can ignore this.

### Install vert.x

Once you've got the pre-requisites installed, you install vert.x by;

1. Unzip the distro somewhere sensible (e.g. your home directory) 
2. Add the vert.x `bin` directory to your `PATH`.

To make sure you've installed it correctly, open another console and type:

    tim@Ethel:~/example$ vertx version
    vert.x 1.0.0.beta.1
    
You should see output something like the above.    

That's it, the boring stuff is out of the way. Now you're ready to go!

## Running vert.x

_Note on terminology: A *verticle* is the name we've given to the components that you write and deploy to vert.x. Think of it like a 'particle', but for vert.x_

There are various ways vert.x verticles can be run so let's dive in with a real example. Iny my experience most people learn better by example than by reading through pages of instructions.

### Running a verticle in its own vert.x instance

The `vertx` command is used to run vert.x verticles as well as start and stop vert.x standalone servers and deploy and undeploy verticles to standalone server (we'll get to the standalone server stuff a bit later on. If you just type `vertx` at a command line you can see all the various different options the command takes.

In particular `vertx run` is used to start a vert.x verticles in *its own instance* of a vert.x server. This is the simplest way to run a verticle.

At minimum `vertx run` takes a single parameter - the name of the main to run. If it's a JavaScript or Ruby verticle then it's just the name of the script, e.g. `server.js` or `server.rb`. (it doesn't have to be called server, you can name it anything as long as it has the right extension). If it's Java the main is the fully qualified class name of the main classs.

The `vertx run` command can take a few optional parameters, they are:

* `-cp <path>` The path on which to search for the main and any other resources used by the verticle. This defaults to `.` (current directory). If your verticles references other scripts, classes or other resources (e.g. jar files) then make sure these are on this path. The path can contain multiple path entries separated by `:` (colon). Each path entry can be an absolute or relative path to a directory containing scripts, or absolute or relative filenames for jar or zip files.
    An example path might be `-cp classes:lib/otherscripts:jars/myjar.jar:jars/otherjar.jar`
    Always use the path to reference any resources that your verticle requires. Please, **do not** put them on the system classpath as this can cause isolation issues between deployed verticles.
    
* `-instances <instances>` The number of instances of the verticle to instantiate in the vert.x server. Each verticle instance is strictly single threaded so to scale your application across available cores you might want to deploy more than one instance. If ommitted a single instance will be deployed. We'll talk more about scaling, later on in this user manual [LINK].

* `-worker` This options determines whether the verticle is a worker verticle or not. Default is false (not a worker). This is discussed in detail later on in the manual. [LINK]   

* `-cluster` This option determines whether the vert.x server which is started will attempt to form a cluster with other vert.x instances on the network. Clustering vert.x instances allows vert.x to form a distributed event bus with other nodes. Default is false (not clustered). This is discussed in detail in the chapter on clustering [LINK].

* `-cluster-port` If the `cluster` option has also been specified then this determines which port will be used for cluster communication with other vert.x instances. Default is `25500`. If you are running more than one vert.x instance on the same host and want to cluster them, then you'll need to make sure each instance has its own cluster port so avoid port conflicts.

* `-cluster-host` If the `cluster` option has also been specified then this determines which host addresses  will be used for cluster communication with other vert.x instances. Default is `0.0.0.0` (all available interfaces).

Here are some examples of `vertx run`:

Run a JavaScript verticle server.js

    vertx run server.js
    
Run 10 instances of a Java verticle specifying classpath
    
    vertx run com.acme.MyVerticle -cp "classes:lib/myjar.jar" -instances 10
    
Run 20 instances of a ruby worker verticle    
    
    vertx run order_worker.rb -instances 20 -worker
    
Run two JavaScript verticles on the same machine and let them cluster together with each other and any other servers on the network
    
    vertx run handler1.js -cluster
    vertx run handler1.js -cluster -cluster-port 25501
    
### Running a verticle in a standalone vert.x server

Often you may have several verticles that make up your application - you could just start up each one in its own vert.x instance by using a separate `vertx run` for each verticle and have them communicate using the distributed event bus, but it might be a bit wasteful to have each one running in is own JVM instance.

In such cases it often makes sense to start up a standalone vert.x instance and deploy verticles to that instance instead.

This can be done using the `vertx start` and `vertx deploy` commands. Let's do that now, using the same `server.js` we created in the previous section.

#### Starting a Standalone vert.x server

To start a standalone vert.x server you simply type the command:

    vertx start    
    
The `vertx start` command also takes a few optional parameters, some of which have the same meaning as the corresponding parameter in the `vertx run` command:

* `-port` This specifies which port the server will listen to for deployments. Default is `25571`. You'd want to change this if you had multiple standalone vert.x servers running on the same host. 

* `-cluster` This has the same meaning of the `-cluster` parameter in `vertx run`. 

* `-cluster-port` This has the same meaning of the `-cluster-port` parameter in `vertx run`. 

* `-cluster-host` This has the same meaning of the `-cluster-host` parameter in `vertx run`. 

Here are some examples:

Start a clustered vert.x server:

    vertx start -cluster
    
Start two unclustered vert.x servers on the same host - they need to be given unique deployment ports

    vertx start
    vertx start -port 25572
    
Start two clustered vert.x servers on the same host - they need to be given unique deployment and clustering ports 

    vertx start -cluster
    vertx start -cluster -port 25572 -cluster-port 25501       

#### Deploying a Verticle to a Standalone server

Once you have a vert.x standalone server running, you can deploy verticles to it. This is done using the `vertx deploy` command.

Any verticles deployed to a standalone server are transient and only last as long as the server is running. You can have as many verticles as you like (subject to available RAM) deployed in the same instance at the same time. The verticles can be in a mixture of languages.

Each verticle instance runs in its own classloader so is isolated from any other verticles running in the instance at the same time. You'll hear about verticle can communicate with each other using shared data and the event bus later on.

The `vertx deploy` command can take a few optional parameters, (some have the same meaning as the parameter with the same name in `vertx run`), they are:

* `-name`. A name to give the deployment. This is subsequently used when undeploying the deployment. If you don't specify a name one will be generated for you, and displayed when you deploy.

* `-port` This specifies which port it will attempt to connect to the server on, to deploy the verticle. Default is `25571`.  

* `-cp <path>` This has the same meaning of the `-cp` parameter in `vertx run`.
    
* `-instances <instances>` This has the same meaning of the `-instances` parameter in `vertx run`.

* `-worker` This has the same meaning of the `-worker` parameter in `vertx run`.

Here are some examples:

Deploy a JavaScript verticle server.js

    vertx deploy server.js
    
Deploy 10 instances of a Java verticle specifying classpath, to a vert.x server instance on port 25600
    
    vertx deploy com.acme.MyVerticle -cp "classes:lib/myjar.jar" -instances 10 -port 25600
    
Deploy 20 instances of a ruby worker verticle    
    
    vertx deploy order_worker.rb -instances 20 -worker
    
Deploy a Ruby verticle specifying a name, then undeploy it

    vertx deploy my_verticle.rb -name my_app
    vertx undeploy my_app    


#### Undeploying Verticles

To undeploy verticles previously deployed using `vertx deploy` you simply type: `vertx undeploy <name>`, where `name` is the name the deployment that you specified using the `-name` parameter, or was generated for you, at deployment time.

Examples:

Undeploy using name specified when deploying

    vertx undeploy my_app
    
Undeploy using generated name which was displayed when deploying    
    
    vertx undeploy app-132855b0-a4ef-4fcf-ad3c-9c6762d2e518
       
    
#### Stopping a Standalone vert.x server

To stop a vert.x standalone server you use the command `vertx stop`. This will cause the server to exit.

The `vertx stop` command can take an optional parameter:

* `-port` This specifies which port it will attempt to connect to the server on, to stop the server. Default is `25571`. If you have more than one vert.x server on localhost then you use this parameter to determine which one to stop.

## Writing Verticles

We previously discussed how a Verticle was the atomic unit in vert.x, and the unit of deployment. We discussed that verticles can be written in several different programming languages and deployed to the same or different servers. Let's look in more detail about how to write a verticle.

Take a look at the simple TCP echo server verticle again.

### JavaScript

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
    
### Ruby

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

### Java  

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
    
## The Event Bus

The event bus is the nervous system of vert.x. It allows verticles to communicate each other irrespective of whether they're in the same vert.x instance, or in a different vert.x instance. It even allows client side JavaScript running in a browser to communicate with verticles. More on that later.

The event bus API is incredibly simple. It basically involves registering handlers, unregistering handlers and sending messages.

First some preliminaries:

### The Theory

#### Addressing

Messages are sent on the event bus to an *address*. vert.x doesn't bother with any fancy addressing schemes. In vert.x an address is simply an arbitrary string, although it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.

Some examples of valid addresses are `europe.news.feed1`, `acme.games.pacman`, `sausages`, and `X`. 

#### Handlers

You register a handler at an address. The handler will be called when any messages which have been sent to that address have been received. Many different handlers from the same or different verticles can be registered at the same address. A single handler can be registered by the verticle at many different addresses.

When a message is received in a handler, and has been *processed*, the receiver can optionally decide to reply to the message. If they do so, and the message was sent specifying a reply handler, that reply handler will be called.

#### Sending messages

You send a message by specifying the address and telling the event bus to send it there. The event bus will then deliver the message to any handlers registered at that address. If multiple vert.x instances are clustered together, the message will be delivered to any matching handlers irrespective of what vert.x instance they reside on.  

The vert.x event bus is therefore an implementation of *Publish-Subscribe Messaging*.

When sending a message you can specify an optional reply handler which will be invoked once the message has reached a handler and the recipient has replied to it.

The vert.x event bus therefore also implements the *Request-Response* messaging pattern.

All messages in the event bus are transient, and in case of failure of all or parts of the event bus, there is every chance the message will be lost. If your application cares about lost messages, you should code your handlers to be idempotent, and your senders to retry after recovery.

Messages that you send on the event bus can be as simple as a string, a number or a boolean. You can also send vert.x buffers [LINK] or JSON Messages [LINK]. If your messages are more than trivial, it's a common convention in vert.x to use JSON messages to communicate between verticles. JSON is easy to create and parse in all the languages that vert.x supports.

### Event Bus API

#### JavaScript

##### Registering and Unregistering Handlers

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

##### Sending messages

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

#### Replying to messages

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

#### Distributed event bus

To make each vert.x instance on your network participate on the same event bus, start each vert.x instance with the `-cluster` command line switch.

See the chatper on *running vert.x* [LINK] for more information on this.    
      
    
## Writing TCP Servers and Clients

Creating TCP servers and clients is incredibly easy with vert.x.

### Net Server


#### Creating a Net Server

To create a TCP server we simply create an instance of vertx.net.NetServer.

    var server = new vertx.NetServer();
    
#### Start the Server Listening    
    
To tell that server to listen on for incoming connections we do:    

    var server = new vertx.NetServer();

    server.listen(1234, 'myhost');
    
The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If it is ommitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


#### Getting Notified of Incoming Connections
    
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

#### Closing a Net Server

To close a net server just call the `close` function.

    server.close();

The close is actually asynchronous and might not complete until some time after the `close` function has returned. If you want to be notified when the actual close has completed then you can pass in a function to the `close` function. This handler will then be called when the close has fully completed.
 
    server.close(function() {
      log.println('The server is now fully closed.');
    });
    
#### NetServer Properties

NetServer has a set of properties you can set which affect its behaviour. Firstly there are bunch of properties used to tweak the TCP parameters, in most cases you won't need to set these:

* `setTCPNoDelay(tcpNoDelay)` If `tcpNoDelay` is true then [Nagle's Algorithm](http://en.wikipedia.org/wiki/Nagle's_algorithm) is disabled. If false then it is enabled.

* `setSendBufferSize(size)` Sets the TCP send buffer size in bytes.

* `setReceiveBufferSize(size)` Sets the TCP received buffer size in bytes.

* `setTCPKeepAlive(keepAlive)` if `keepAlive` is true then [TCP keep alive](http://en.wikipedia.org/wiki/Keepalive#TCP_keepalive) is enabled, if false it is disabled. 

* `setReuseAddress(reuse)` if `reuse` is true then addresses in TIME_WAIT state can be reused after they have been closed.

* `setSoLinger(linger)`

* `setTrafficClass(trafficClass)`

NetServer has a further set of properties which are used to configure SSL. We'll discuss those later on.

#### Handling Data

So far we have seen how to create a NetServer, and accept incoming connections, but not how to do anything interesting with the connections, let's do that now.

When a connection is made, the connect handler is called passing in an instance of `NetSocket`. This is a socket-like interface to the actual connection, and allows you to read and write data as well as various other operations.

##### Reading Data from the Socket

To read data from the socket you need to set the `dataHandler` on the socket. This handler will be called with a `Buffer` [LINK] every time data is received on the socket. You could try the following code and telnet to it to send some data:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
    
      sock.dataHandler(function(buffer) {
        log.println('I received ' + buffer.length() + ' bytes of data');
      });
      
    }).listen(1234, 'localhost');
    
##### Writing Data to a Socket

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
    
#### Closing a socket

You can close a socket by invoking the `close` method. This will close the underlying TCP connection.

#### Closed Handler

If you want to be notified when a socket is closed, you can set the `closedHandler':


    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
        
        sock.closedHandler(function() {
        
            log.println('The socket is now closed');    
        
        });
    });

The closed handler will be called irrespective of whether the close was initiated by the client or server.

#### Exception handler

You can set an exception handler on the socket that will be called if an exception occurs:

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
        
        sock.exceptionHandler(function() {
        
            log.println('Oops. Something went wrong');    
        
        });
    });

    
#### Read and Write Streams

NetSocket also can at as a `ReadStream` and a `WriteStream`. This allows flow control to occur on the connection and the connection data to be pumped to and from other object such as HTTP requests and responses, websockets and asynchronous files.

This will be discussed in depth in the chapter on streams [LINK] 

#### Scaling TCP Servers

A verticle instance is strictly single threaded. If I create a simple TCP server and deploy a single instance of it then all the handlers for that server are always executed on the same event loop (thread). This means that if you are running on a server with a lot of cores, and you only have this one instance deployed then you will have at most one core utilised on your server! That's not very good.

To remedy this you can simply deploy more instances of the verticle in the server, e.g.

    vertx deploy echo_server.js -instances 20
    
Would deploy 20 instances of echo_server.js to a locally running vert.x instance. Once you do this you will find the echo server works functionally identically to before, but, as if by magic all your cores on your server can be utilised and more work can be handled.

By this point you might be asking yourself *'Hang on a second, how can you have more than one server listening on the same host and port? Surely you will get port conflicts as soon as they try and deploy more than one instance?'*

*Vert.x does a little magic here*. When you deploy another server on the same host and port as an existing server it doesn't actually try and create a new server listening on the same host/port, instead it internally maintains just a single server, and, as incoming connections arrive it distributes them in a round-robin fashion to any of the connect handlers set by the verticles. Consequently vert.x TCP servers can scale over available cores while each vert.x verticle instance remains strictly single threaded :)

    
### NetClient

A NetClient is used to make TCP connections to servers.

#### Creating a Net Client

To create a TCP server we simply create an instance of vertx.net.NetClient.

    var client = new vertx.NetClient();

#### Making a Connection

To actually connect to a server you invoke the `connect` method:

    var client = new vertx.NetClient();
    
    client.connect(1234, 'localhost', function(sock) {
        log.println('We have connected');
    });
    
The connect method takes the port number as the first parameter, followed by the hostname or ipaddress of the server. The third parameter is a connect handler. This handler will be called when the connection actually occurs.

The argument passed into the connect handler is an instance of `NetSocket`, exactly the same as what is passed into the server side connect handler. Once given the `NetSocket` you can read and write data from the socket in exactly the same way as you do on the server side.

You can also close it, set the closed handler, set the exception handler and use it as a `ReadStream` or `WriteStream` exactly the same as the server side `NetSocket`.

#### Catching exceptions on the Net Client

You can set a connection handler on the NetClient. This will catch any exceptions that occur during connection.

    var client = new vertx.NetClient();
    
    client.exceptionHandler(function(ex) {
      log.println('Cannot connect since the host was made up!');
    });
    
    client.connect(4242, 'host-that-doesnt-exist', function(sock) {
      log.println('this won't get called');
    });


#### Configuring Reconnection

A NetClient can be configured to automatically retry connecting to the server in the event that it cannot connect or has lost its connection. This is done by invoking the functions `setReconnectAttempts` and `setReconnectInterval`

    var client = new vertx.NetClient();
    
    client.setReconnectAttempts(1000);
    
    client.setReconnectInterval(500);
    
`ReconnectAttempts` determines how many times the client will try to connect to the server before giving up. A value of `-1` represents an infinite number of times.

`ReconnectInterval` detemines how long, in milliseconds, the client will wait between reconnect attempts.

The default value for `ReconnectAttempts` is `0`. I.e. no reconnection is attempted.

If an exception handler is set on the client, and reconnect attempts is not equal to `0`. Then the exception handler will not be called until the client gives up reconnecting.


#### NetClient Properties

Just like NetServer, NetClient also has a set of TCP properties you can set which affect its behaviour. They have the same meaning as those on NetServer.

NetServer also has a further set of properties which are used to configure SSL. We'll discuss those later on.

### SSL Servers

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

### SSL Clients

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
                     
 

## Flow Control - Streams and Pumps

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

### ReadStream

`ReadStream` is implemented by `AsyncFile`, `HttpClientResponse`, `HttpServerRequest`, `WebSocket`, `NetSocket` and `SockJSSocket`.

Functions:

* `dataHandler(handler)`: set a handler which will receive data from the `ReadStream`. As data arrives the handler will be passed a Buffer.
* `pause()`: pause the handler. When paused no data will be received in the `dataHandler`.
* `resume()`: resume the handler. The handler will be called if any data arrives.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `ReadStream`.
* `endHandler(handler)`: Will be called when end of stream is reached. This might be when EOF is reached if the `ReadStream` represents a file, or when end of request is reached if its an HTTP request, or when the connection is closed if its a TCP socket.

### WriteStream

`WriteStream` is implemented by `AsyncFile`, `HttpClientRequest`, `HttpServerResponse`, `WebSocket`, `NetSocket` and `SockJSSocket`

Functions:

* `writeBuffer(buffer)`: write a Buffer to the `WriteStream`. This method will never block. Writes are queued internally and asynchronously written to the underlying resource.
* `setWriteQueueMaxSize(size)`: set the number of bytes at which the write queue is considered *full*, and the function `writeQueueFull()` returns `true`. Note that, even if the write queue is considered full, if `writeBuffer` is called the data will still be accepted and queued.
* `writeQueueFull()`: returns `true` if the write queue is considered full.
* `exceptionHandler(handler)`: Will be called if an exception occurs on the `ReadStream`.
* `endHandler(handler)`: Will be called when end of stream is reached. This might be when EOF is reached if the `ReadStream` represents a file, or when end of request is reached if its an HTTP request, or when the connection is closed if its a TCP socket.

### Pump

Instances of `Pump` have the following methods:

* `start()`. The start the pump. When the pump is not started, the `ReadStream` is paused.
* `stop(). Stops the pump. Resumes the `ReadStream`.
* `setWriteQueueMaxSize()`. This has the same meaning as `setWriteQueueMaxSize` on the `WriteStream`.
* `getBytesPumped()`. Returns total number of bytes pumped.

## Shared Data

Sometimes it makes sense to allow different verticles instances to share data in a safe way. vert.x allows simple *Map* and *Set* data structures to be shared between verticles.

There is a caveat: To prevent issues due to mutable data, vert.x only simple immutable types such as number, boolean and string, or Buffer to be used in shared data. With a Buffer, it is automatically copied when retrieved from the shared data.

TODO - a bit more here

API - atomic updates etc

## Writing HTTP Servers and Clients


### Writing HTTP servers

Writing full featured, highly performant and scalable HTTP servers is child's play with vert.x

#### Creating a Net Server

To create an HTTP server we simply create an instance of vertx.net.HttpServer.

    var server = new vertx.HttpServer();
    
#### Start the Server Listening    
    
To tell that server to listen on for incoming requests we do:    

    var server = new vertx.HttpServer();

    server.listen(8080, 'myhost');
    
The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If the hostname is ommitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


#### Getting Notified of Incoming Requests
    
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
       
#### Handling HTTP Requests

So far we have seen how to create an HTTPServer and be notified of requests but not how to do anything interesting with the requests. 

When a request arrives, the request handler is called passing in an instance of `HttpServerRequest`. This object represents the server side HTTP request. It contains functions to get the uri, path, request headers and request parameters. It also contains a `response` property which is a reference to an object that represents the server side HTTP response for the object.

##### Request Method

The request object has a property `method` which is a string representing what HTTP method was requested. Possible values are GET, PUT, POST, DELETE, HEAD, OPTIONS, CONNECT, TRACE, PATCH.

##### Request URI

The request object has a property `uri` which contains the full uri of the request. For example, if the request uri was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.uri` would contain the string `http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz`.

##### Request Path

The request object has a property `path` which contains the path of the request. For example, if the request uri was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.path` would contain the string `/a/b/c/page.html`
   
##### Request Query

The request object has a property `query` which contains the query of the request. For example, if the request uri was:

    http://localhost:8080/a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.query` would contain the string `param1=abc&param2=xyz`    
        
##### Request Headers

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
    
##### Request params

Similarly to the headers, the request parameters are available using the `params()` function on the request object. The return value of the function is just a JavaScript hash.     

Request parameters are sent on the request uri, after the path. For example if the uri was:

    http://localhost:8080/page.html?param1=abc&param2=xyz
    
Then the params hash would be the following object:

    { param1: 'abc', param2: 'xyz' }
    
##### Reading Data from the Request Body

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
    
#### Writing HTTP Responses 

As previously mentioned, the HTTP request object contains a property `response`. This is the HTTP response for the request. You use it to write the response back to the client.

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


#### Serving files directly disk

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

#### Pumping Responses

Since the HTTP Response implements `WriteStream` you can pump to it from any `ReadStream`, e.g. an `AsyncFile`, `NetSocket` or `HttpServerRequest`. Here's an example which simply echoes an HttpRequest headers and body back in the HttpResponse. Since it uses a pump for the body, it will work even if the HTTP request body is much larger than can fit in memory at any one time:

    var server = new vertx.HttpServer();

    server.requestHandler(function(req) {
      
      req.response.putHeaders(req.headers());
      
      var p = new Pump(req, req.response);
      p.start();
      
    }).listen(8080, 'localhost');
    
### Writing HTTP Clients

#### Creating an HTTP Client

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

#### Pooling and Keep Alive

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

#### Closing the client

Once you have finished with an HTTP client, you should close it:

    client.close();             

                         
#### Making Requests

To make a request using the client you invoke one the methods named after the HTTP methods. For example, to make a POST request:

    var client = new vertx.HttpClient();
    
    var request = client.post('http://localhost:8080/some-uri', function(resp) {
        log.println('Got a response, status code: ' + resp.statusCode);
    });
    
    request.end();
    
To make a PUT request use the `put` method, to make a GET request use the `get` method, etc.

The general modus operandi is you invoke the appropriate method passing in the request URI as the first parameter, the second parameter is an event handler which will get called when the corresponding response arrives. The response handler is passed the client response object as an argument.

The return value from the appropriate request method is a client request object. You can use this to add headers to the request, and to write to the request body.

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

##### Writing to the request body

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

##### Writing Request Headers

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


##### HTTP chunked requests

vert.x also supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding) for requests. This allows the HTTP request body to be written in chunks, and is normally used when a large request body is being streamed to the server, whose size is not known in advance.

You put the HTTP request into chunked mode as follows:

    request.setChunked(true);
    
Default is non-chunked. When in chunked mode, each call to `request.write(...)` will result in a new HTTP chunk being written out.  

##### Reading Data from the Response Body

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

### HTTPS Servers

HTTP Servers can also be configured to work with [Transport Layer Security](http://en.wikipedia.org/wiki/Transport_Layer_Security) (previously known as SSL).

When an HTTP Server is working as an HTTPS Server the api of the server is identical compared to when it working with standard HTTP. Getting the server to use HTTPS is just a matter of configuring the HTTP Server before listen is called.

Configuration of an HTTPS server is exactly the same as configuring a Net Server for SSL. Please see SSL server chapter for detailed instructions.

### HTTPS Clients

HTTP Clients can also be easily configured to use HTTPS.

Configuration of an HTTPS client is exactly the same as configuring a Net Client for SSL. Please see SSL client chapter for detailed instructions. 

### Scaling HTTP servers

Scaling an HTTP server over multiple cores is as simple as deploying more instances of the verticle. For example:

    vertx deploy http_server.js -instances 20
    
The scaling works in the same way as scaling a Net Server. Please see the section on scaling Net Servers for a detailed explanation [LINK].    

## WebSockets

[WebSockets](http://en.wikipedia.org/wiki/WebSocket) are a new feature in HTML 5 that allows a full duplex socket-like connection between HTTP servers and HTTP clients (typically browsers).

vert.x implements the most common versions of websockets on both the server and client side.

[[VERSION NUMBERS]]

### WebSockets on the server

To receive WebSocket connections on the server, you create an HTTP server instance as normal, but instead of setting a `requestHandler`, you set a `websocketHandler`. For details on how to create an HTTP server please see the [LINK chapter.

    var server = new vertx.HttpServer();

    server.websocketHandler(function(request) {
    
    }).listen(8080, 'localhost');   

Hmmm, need to pass in two params - or should we pass in 1??

******

CAN'T WE JUST HAVE A METHOD REJECT ON WEBSOCKET, IF CALLED WITHIN WEBSOCKETHANDLER THEN WEBSOCKET WILL BE REJECTED.

WE CAN JUST CHECK IF REJECT HAS BEEN CALLED BY THE TIME THE HANDLER HAS BEEN CALLED, AND IF SO REJECT IT. WRITING FROM THE HANLER AUTOMATICALLY ACCEPTS IT, REJECT CAN'T BE CALLED AFTER THAT.we've got to do it this way


### WebSockets on the HTTP client

### WebSockets in the browser


## SockJS







    
            
    








    
    

    








    














        
    


    
    



    




    
    

         
       






