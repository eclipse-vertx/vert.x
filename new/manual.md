[TOC]

# Introduction 
   
## What is vert.x? 
      
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

List the elements of core:

vert.x core comprises:

* Net - writing TCP servers and clients
* HTTP

etc

**TODO**


# Installation

Before you can do anything with vert.x you need to install it, so let's get that out of the way.

## Getting a distro

You can't install it if you haven't got it, so first you need to download it.  

The easiest way is to download a distro from the download page [link]. Alternatively you can build from source. To do that see the instructions on the github wiki.

## Pre-requisites  

* Operating System. vert.x runs out of the box on Linux or OSX. If you are running Windows, the best way to run vert.x is to create a Linux (I recommend Ubuntu) vitrtual machine using your favourite virtualisation software (VMware Workstation, of course!) and run it in that.

* JDK. vert.x requires JDK 1.7.0 or later. You can use the official Oracle distribution or the OpenJDK version. Make sure the JDK bin directory is on your `PATH`.

* Apache Ant. If you want to run the Java examples you will need Apache Ant installed. Otherwise you don't need it.

* Ruby. If you want to deploy Ruby verticles then you will need JRuby 1.6.4 later installed. Please set the `JRUBY_HOME` environment variable to point at the base of the JRuby installation. If you don't intend to deploy Ruby verticles you can ignore this.

## Install vert.x

Once you've got the pre-requisites installed, you install vert.x by;

1. Unzip the distro somewhere sensible (e.g. your home directory) 
2. Add the vert.x `bin` directory to your `PATH`.

To make sure you've installed it correctly, open another console and type:

    tim@Ethel:~/example$ vertx version
    vert.x 1.0.0.beta.1
    
You should see output something like the above.    

That's it, the boring stuff is out of the way. Now you're ready to go!

# Running vert.x

_Note on terminology: A *verticle* is the name we've given to the components that you write and deploy to vert.x. Think of it like a 'particle', but for vert.x_

There are various ways vert.x verticles can be run so let's dive in with a real example. Iny my experience most people learn better by example than by reading through pages of instructions.

## Running a verticle in its own vert.x instance

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
    
## Running a verticle in a standalone vert.x server

Often you may have several verticles that make up your application - you could just start up each one in its own vert.x instance by using a separate `vertx run` for each verticle and have them communicate using the distributed event bus, but it might be a bit wasteful to have each one running in is own JVM instance.

In such cases it often makes sense to start up a standalone vert.x instance and deploy verticles to that instance instead.

This can be done using the `vertx start` and `vertx deploy` commands. Let's do that now, using the same `server.js` we created in the previous section.

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

### Deploying a Verticle to a Standalone server

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


### Undeploying Verticles

To undeploy verticles previously deployed using `vertx deploy` you simply type: `vertx undeploy <name>`, where `name` is the name the deployment that you specified using the `-name` parameter, or was generated for you, at deployment time.

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

By default [JUL] (http://docs.oracle.com/javase/1.4.2/docs/guide/util/logging/overview.html) logging is used. This can be configured using the file `VERTX_HOME\conf\logging.properties`. Where `VERTX_HOME` is the directory in which you installed vert.x.

Advanced note: If you'd rather use a different logging framework, e.g. log4j you can do this by specifying a system property when running vert.x (edit the vertx.sh script), e.g.

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.Log4jLogDelegateFactory
    
or

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.SLF4JLogDelegateFactory    

# JavaScript Core Guide

[JavaScript Core Guide here](core_manual_js.html)

# Ruby Core Guide

[Ruby Core Guide here](core_manual_ruby.html)

# Java Core Guide

[Java Core Guide here](core_manual_java.html)

# Bus Modules Guide

[Bus Modules Guide here](bus_mods_manual.html)


    
    

    








    














        
    


    
    



    




    
    

         
       






