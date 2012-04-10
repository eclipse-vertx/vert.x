[TOC]

# Introduction 
   
## What is vert.x? 
      
**Vert.x is the framework for the next generation of asynchronous, effortlessly scalable, concurrent applications.**

Vert.x is a framework which takes inspiration from event driven frameworks like node.js, combines it with a distributed event bus and sticks it all on the JVM - a runtime with *real* concurrency and unrivalled performance. Vert.x then exposes the API in Ruby, Java and Groovy as well as JavaScript.
      
Some of the key highlights include:

* Polyglot. Write your application components in JavaScript, Ruby, Groovy or Java. It's up to you. Or mix and match several programming languages in a single application. (Scala and Clojure support is scheduled too).

* Super simple concurrency model. Vert.x allows you to write all your code as single threaded, freeing you from the hassle of multi-threaded programming.

* Vert.x has a super simple, asynchronous programming model for writing truly scalable non-blocking applications.

* Vert.x includes a distributed event bus that spans the client and server side so your applications components can communicate incredibly easily. The event bus even penetrates into in-browser JavaScript allowing you to create effortless so-called *real-time* web applications.

* Vert.x provides real power and simplicity, without being simplistic. No more boilerplate or sprawling xml configuration files.

## Vert.x Embedded

If you don't want the whole vert.x platform but just want to use HTTP, HTTPS, TCP, SSL, WebSockets, event bus, or other vert.x functionality directly in your own pre-existing Spring Framework or other Java application, then you can do this too.

Just use the jar `vertx-core.jar` which is available in the `lib/jars` directory in the distribution.

You then have full access to the core vert.x API, in either Java or Groovy. If you use vert.x embedded you don't have to worry about verticles or any of the deployment related topics, and can just use the core API directly.

There is a caveat here. When running vert.x outside a verticle you don't have the isolation benefits that a verticle brings, so you will need to be more careful about sharing state between different event loops.

## Concepts in vert.x

In this section I'll give an overview of the main concepts in vert.x. Many of these concepts will be discussed in more depth later on in this manual.

### Verticle

The unit of deployment in vert.x is called a *verticle* (Think of a particle, for vert.x). Verticles can currently be written in JavaScript, Ruby or Java (We have some partial Groovy support and we're looking to support Scala and Clojure going ahead).

A verticle is defined by having a *main* which is just the script (or class in the case of Java) to run to start the verticle. A verticle can also contain other scripts which are referenced from the main. It can also contain any jars, and other resources that are used by the verticle.

When you write an application, you might write a single verticle, or your application could consist of a whole set of verticles communicating with each other using the event bus.

### Vert.x Instances

Verticles run inside a vert.x *instance*. A single vert.x instance runs inside its own JVM instance. There can be many verticles running inside a single vert.x instance at any one time. Vert.x makes sure each verticle is isolated by giving it its own classloader so they can't interact by sharing static members, global variables or other means.

There can be many vert.x instances running on the same host, or on different hosts on the network at the same time. The instances can be configured to cluster with each other forming a distributed event bus over which verticle instances can communicate.

### Polyglot

We want you to be able to develop your verticles in a choice of programming languages. Never have developers had such a choice of great languages, and we want that to be reflected in the languages we support. Vert.x allows you to write verticles in JavaScript, Ruby and Java, but we aim to support Groovy, Clojure, Scala and Python going ahead. These verticles can seamlessly interoperate with other verticles irrespective of what language they are written in.

### Concurrency

A vert.x instance guarantees that a particular verticle instance is always executed by the exact same thread. This gives you a huge advantage as a developer, since you can program all your code as single threaded. Well, that won't be a big deal to you if you are coming from JavaScript where everything is single threaded, but if you're used to multi-threaded programming in Java, Scala, or even Ruby, this may come as a huge relief since you don't have to synchronize access to your state. This means a whole class of race conditions disappear, and OS thread deadlocks are a thing of the past. 

### Event-based Programming Model

Vert.x provides an event-based programming model, similar to frameworks such as node.js.

Most things you do in vert.x involve setting event handlers. For example, to receive data from a TCP socket you set a handler - the handler is then called when data arrives.

You also set handlers to receive messages from the event bus, to receive HTTP requests and responses, to be notified when a connection is closed, or to be notified when a timer fires. There are many examples throughout the vert.x api.

Any other operations in vert.x that don't involve handlers, e.g. writing some data to a socket are guaranteed never to block.

Why have been chosen an event based approach? The answer is, if we want our application to scale with many connections, *we don't have a choice*.

Let's imagine for a minute that the vert.x api allowed a blocking read on a TCP socket. When the code in a verticle called that blocking operation and no data arrived for, say, 1 minute, it means that thread cannot do anything else during that time - it can't do work for any other verticle.

For such a blocking model to work and the system to remain responsive, each verticle instance would need to be assigned its own thread. Now consider what happens when we have thousands, 10s of thousands, or 100s of thousands of verticles running. We clearly can't have that many threads - the overhead due to context switching and stack space would be horrendous. It's clear to see such a blocking model just doesn't scale.

The only way to make it scale is have a 100% non blocking api. There are two ways to do this:

* Use an asynchronous, event based API. Let the system call you when events occur. Do not block waiting for things to happen.

* Use [continuations] (http://en.wikipedia.org/wiki/Continuation). Continuations allow you to suspend the execution of a piece of code and come back to a later when an event occurs. However continuations are not currently supported across all the different languages, or versions of languages that we support in vert.x.

Vert.x currently takes the event-based api approach. As support for continuations in various languages matures we will consider also supporting them in the api.

### Event Loops

Internally, a vert.x instance manages a small set of threads, normally matching the number of threads to the available cores on the server. We call these threads *event loops*, since they basically just loop around (well... they do actually go to sleep if there is nothing to do) seeing if there is any work to do, for example, handling some dcata that's been read from a socket, or executing a timer.

When a verticle instance is deployed, the server chooses an event loop which will be assigned to that instance. Any subsequent work to be done for that instance will always be dispatched using that thread. Of course, since there are potentially many thousands of verticles running at any one time, a single event loop is assigned to many verticles at the same time.

We call this the *multi-reactor pattern*. It's like the [reactor pattern](http://en.wikipedia.org/wiki/Reactor_pattern) but there's more than one event loop.

### Message Passing

Verticles can communicate with other verticles running in the same, or different vert.x instance using the event bus. If you think of each verticle as an actor, it in some ways resembles the [actor model](http://en.wikipedia.org/wiki/Actor_model) as popularised by the Erlang programming language.

By having many verticle instances in a vert.x server instance and allowing message passing allows the system to scale well over available cores without having to allow multi-threaded execution of any verticle code. 

### Shared data

Message passing is extremely useful, but it's not always the best approach to concurrency for all types of applications.

An example would be an application that wishes to provide an in-memory web cache.

As requests for a resource arrive at a server, the server looks up the resource in the cache and returns it from there if the item is present, if the item is not present it loads it from disk and places it in the cache for the next time.

We want this application to scale across all available cores. Modelling this using message passing is problematic. At one end of the scale we could have a single verticle that manages the cache, but this means all requests to the cache will be serialized through a single threaded verticle instance. We could improve things by having multiple instances of the verticle managing different parts of the cache, but it quickly gets ugly and complicated.

Such a use case is better solved by providing a shared map structure that can be accessed directly by different verticle instances in the same vert.x instance. As requests come in, the data can be efficiently looked up in the cache with a single line of code and returned to the user.

It's fashionable these days to deride shared data. But shared data is only dangerous if the data that you share is mutable.

Vert.x provides a shared map and shared set facility which allows only *immutable* data to be shared between verticles.

### Worker Verticles

Vert.x uses only a small number of event loop threads, and has to dispatch events to potentially thousands of verticles, and remain responsive.

When you consider that, it's pretty clear that those threads can't spend too much time in any particular verticle event handler- if they do it means that the event loop can't service events for other verticles, and the whole system can grind to a halt.

How do we define *too much time*? It's hard to put an exact figure on it, but one rule of thumb is anything that takes more than a few milliseconds of *wall-clock* time. (That's actual elapsed time, not CPU time).

That time includes the thread sleeping or blocking on a database operation (where few CPU cycles are involved), but it also involves *busy waits* such as a computationally intensive operation, e.g. factorising a prime number.

By default, verticle event handlers should not take a long time to execute, however there are cases where you can't avoid blocking, or you genuinely have computationally intensive operations to perform.

An example of the former would be calling a third-party blocking database API from a verticle. In this case you don't have control of the client library you are using so you just have to block until you get the result back.

Another example would be a worker verticle which needs to do an intensive calculation like calculating Fibonacci numbers. In such a case the calculation could be done a little at a time on different circuits around the event loop, but this is awkward, and just a little bit silly ;)

For cases like these, vert.x allows you to mark a particular verticle instance as a *worker verticle*. A worker verticle differs from a normal verticle in that it is not assigned a vert.x event loop thread, instead it executes on a thread from an internal thread pool called the *background pool*. 

Worker verticles are never executed concurrently by more than one thread. Worker verticles are also not allowed to use TCP or HTTP clients or servers. Worker verticles normally communicate with other verticles using the vert.x event bus, e.g. receiving work to process.

Worker verticles should be kept to a minimum, since a blocking approach doesn't scale if you want to deal with many concurrent connections.

### Core and BusMods

Vert.x modules can be conceptually divided into two types: *Core Services*, and *BusMods*.

#### Vert.x Core

Vert.x core provides a set of services which can be directly called from code in a verticle. The API for core is provided in each of the programming languages that vert.x supports.

The API will be described in detail in the Core API manual, but includes services such as:

* TCP/SSL servers and clients
* HTTP/HTTPS servers and clients
* WebSockets servers and clients
* Accessing the distributed event bus
* Periodic and one-off timers
* Buffers
* Flow control
* Accessing files on the file system
* Shared map and sets
* Logging
* Accessing configuration
* Writing SockJS servers
* Deploying/Undeploying verticles

Vert.x core is fairly static and is not envisaged that it will grow much over time. This is good since every service in core has to be provided in each of the languages we support - that's a lot of API adaptors to write.

#### BusMods

*BusMod* is short for *Bus Module*. It's basically a service that you communicate with by exchanging JSON messages over the event bus. Since all communication is over the event bus, and not by direct calls like with vert.x core, it means it can be written once in any language, and any verticle in any other language can immediately use it without an API adaptor having to be written for each language.

Whilst vert.x core is fairly static, we envisage that a wide range of busmods will be available in vert.x for performing common operations. Vert.x already ships with several busmods out-of-the-box, including a persistor, a mailer and work queues, and we hope to ship with many more over time.

We also encourage the community to create and contribute their own busmods for others to use.

For more information on busmods please see the busmod manual.

# Running vert.x

There are various ways vert.x verticles can be run, let's describe them.

## Running a verticle in its own vert.x instance

The `vertx` command is used to interact with vert.x from the command line. It's used to run vert.x verticles as well as start and stop vert.x standalone servers and deploy and undeploy verticles to standalone servers (we'll get to the standalone server stuff a bit later on).

If you just type `vertx` at a command line you can see the different options the command takes.

The command `vertx run` is used to start a vert.x verticle in *its own instance* of a vert.x server. This is the simplest way to run a verticle.

At minimum `vertx run` takes a single parameter - the name of the main to run. If it's a JavaScript or Ruby verticle then it's just the name of the script, e.g. `server.js` or `server.rb`. (it doesn't have to be called server, you can name it anything as long as it has the right extension). If it's Java the main is the fully qualified class name of the main classs.

The `vertx run` command can take a few optional parameters, they are:

* `-config <config_file>` Provide some configuration to the verticle. `config_file` is the name of a text file containing a JSON object that represents the configuration for the verticle. This is optional.

* `-cp <path>` The path on which to search for the main and any other resources used by the verticle. This defaults to `.` (current directory). If your verticle references other scripts, classes or other resources (e.g. jar files) then make sure these are on this path. The path can contain multiple path entries separated by `:` (colon). Each path entry can be an absolute or relative path to a directory containing scripts, or absolute or relative filenames for jar or zip files.
    An example path might be `-cp classes:lib/otherscripts:jars/myjar.jar:jars/otherjar.jar`
    Always use the path to reference any resources that your verticle requires. Please, **do not** put them on the system classpath as this can cause isolation issues between deployed verticles.
    
* `-instances <instances>` The number of instances of the verticle to instantiate in the vert.x server. Each verticle instance is strictly single threaded so to scale your application across available cores you might want to deploy more than one instance. If ommitted a single instance will be deployed. We'll talk more about scaling later on in this user manual.

* `-worker` This options determines whether the verticle is a worker verticle or not. Default is false (not a worker). This is discussed in detail later on in the manual.  

* `-cluster` This option determines whether the vert.x server which is started will attempt to form a cluster with other vert.x instances on the network. Clustering vert.x instances allows vert.x to form a distributed event bus with other nodes. Default is false (not clustered).

* `-cluster-port` If the `cluster` option has also been specified then this determines which port will be used for cluster communication with other vert.x instances. Default is `25500`. If you are running more than one vert.x instance on the same host and want to cluster them, then you'll need to make sure each instance has its own cluster port to avoid port conflicts.

* `-cluster-host` If the `cluster` option has also been specified then this determines which host addresses  will be used for cluster communication with other vert.x instances. Default is `0.0.0.0` (all available interfaces).

Here are some examples of `vertx run`:

Run a JavaScript verticle server.js with default settings

<pre class="prettyprint lang-html">
vertx run server.js
</pre>
    
Run 10 instances of a Java verticle specifying classpath

<pre class="prettyprint lang-html">
vertx run com.acme.MyVerticle -cp "classes:lib/myjar.jar" -instances 10
</pre>
    
Run 20 instances of a ruby worker verticle    
    
    vertx run order_worker.rb -instances 20 -worker
    
Run two JavaScript verticles on the same machine and let them cluster together with each other and any other servers on the network
    
    vertx run handler1.js -cluster
    vertx run handler1.js -cluster -cluster-port 25501
    
Run a Ruby verticle passing it some config

    vertx run my_vert.rb -config my_vert.conf
    
Where `my_vert.conf` might contain something like:

    {
        "name": "foo",
        "num_widgets": 46
    }    
    
The config will be available inside the verticle via the core API.    
    
## Running a verticle in a standalone vert.x server

Often you may have several verticles that make up your application - you could just start up each one in its own vert.x instance by using a separate `vertx run` for each verticle and have them communicate using the distributed event bus, but it might be a bit wasteful to have each one running in its own JVM instance.

In such cases it often makes sense to start up a standalone vert.x instance and deploy multiple verticles to that instance.

This can be done using the `vertx start` and `vertx deploy` commands.

### Starting a Standalone vert.x server

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
    
Once the vert.x server has started, the `vertx start` command will block until the server is stopped.       

### Deploying a Verticle to a Standalone server

Once you have a vert.x standalone server running, you can deploy verticles to it. This is done using the `vertx deploy` command.

Any verticles deployed to a standalone server are transient and only last as long as the server is running. You can have as many verticles as you like (subject to available RAM) deployed in the same instance at the same time. The verticles can be in a mixture of languages.

Each verticle instance runs in its own classloader so is isolated from any other verticles running in the instance at the same time. You'll hear about how verticles can communicate with each other using shared data and the event bus later on.

The `vertx deploy` command can take a few optional parameters, (some have the same meaning as the parameter with the same name in `vertx run`), they are:

* `-name`. A name to give the deployment. This is subsequently used when undeploying the deployment. If you don't specify a name one will be generated for you, and displayed when you deploy.

* `-port` This specifies which port it will attempt to connect to the server on, to deploy the verticle. Default is `25571`.  

* `-cp <path>` This has the same meaning as the `-cp` parameter in `vertx run`.
    
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


### Undeploying Verticles

To undeploy verticles previously deployed using `vertx deploy` you simply use: `vertx undeploy <name>`, where `name` is the name of the deployment that you specified using the `-name` parameter, or was generated for you, at deployment time.

Examples:

Undeploy using name specified when deploying

    vertx undeploy my_app
    
Undeploy using generated name which was displayed when deploying    
    
    vertx undeploy app-132855b0-a4ef-4fcf-ad3c-9c6762d2e518
       
    
### Stopping a Standalone vert.x server

To stop a vert.x standalone server you use the command `vertx stop`. This will cause the server to exit.

The `vertx stop` command can take an optional parameter:

* `-port` This specifies which port it will attempt to connect to the server on, to stop the server. Default is `25571`. If you have more than one vert.x server on localhost then you use this parameter to determine which one to stop.

# Logging

Each verticle gets its own logger which can be retrieved from inside the verticle. For information on how to get the logger please see the core guide for the language you are using.

The log files by default go in a file called `vertx.log` in the system temp directory. On my Linux box this is `\tmp`.

By default [JUL](http://docs.oracle.com/javase/7/docs/technotes/guides/logging/overview.html) logging is used. This can be configured using the file `$VERTX_HOME\conf\logging.properties`. Where `VERTX_HOME` is the directory in which you installed vert.x.

Advanced note: If you'd rather use a different logging framework, e.g. log4j you can do this by specifying a system property when running vert.x (edit the vertx.sh script), e.g.

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.Log4jLogDelegateFactory
    
or

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.SLF4JLogDelegateFactory    
    
# Configuring clustering

To configure clustering use the file `conf/cluster.xml` in the distribution.

If you want to receive more info on cluster setup etc, then edit `conf/logging.properties` to read `com.hazelcast.level=INFO`

In particular when running clustered, and you have more than one network interface to choose from, make sure Hazelcast is using the correct interface by editing the `interfaces-enabled` element.

# Internals

Vert.x uses the following amazing open source projects:

* [Netty](https://github.com/netty/netty) for much of its network IO
* [JRuby](http://jruby.org/) for its Ruby engine
* [Mozilla Rhino](http://www.mozilla.org/rhino/) for its JavaScript engine
* [Hazelcast](http://www.hazelcast.com/) for group management of cluster members

*Copies of this document may be made for your own use and for distribution to others, provided that you do not charge any fee for such copies and further provided that each copy contains this Copyright Notice, whether distributed in print or electronically.*





    














        
    


    
    



    




    
    

         
       






