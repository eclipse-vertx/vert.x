<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Introduction 
   
## What is vert.x? 
      
**Vert.x is the framework for the next generation of asynchronous, effortlessly scalable, concurrent applications.**

Vert.x is an event driven application framework that runs on the JVM - a run-time with *real* concurrency and unrivalled performance. Vert.x then exposes the API in Ruby, Java, Groovy, JavaScript and Python. So *you* choose what language you want to use. Scala and Clojure support is on the roadmap too.
      
Some of the key highlights include:

* Polyglot. Write your application components in JavaScript, Ruby, Groovy, Java or Python. It's up to you. Or mix and match several programming languages in a single application.

* Super simple concurrency model. Vert.x allows you to write all your code as single threaded, freeing you from the hassle of multi-threaded programming. (No more `synchronized`, `volatile` or explicit locking). 

* Unlike other popular event driven frameworks, Vert.x takes advantage of the JVM and scales seamlessly over available cores without having to manually fork multiple servers and handle inter process communication between them.

* Vert.x has a simple, asynchronous programming model for writing truly scalable non-blocking applications.

* Vert.x includes a distributed event bus that spans the client and server side so your applications components can communicate incredibly easily. The event bus even penetrates into in-browser JavaScript allowing you to create effortless so-called *real-time* web applications.

* Vert.x provides real power and simplicity, without being simplistic. No more sprawling xml configuration files.

* Vert.x includes a module system and public module repository, so you can easily re-use and share Vert.x modules with others.

*Future applications will largely be running on mobile and embedded devices. These demand a platform that can scale with 10s, 100s or even millions of concurrent connections, and allow developers to write scalable, performant applications for them incredibly easily, in whatever language they prefer.*

**We believe Vert.x is that platform.**

## Vert.x Embedded

If you don't want the whole vert.x platform but just want to use HTTP, HTTPS, TCP, SSL, WebSockets, the event bus, or other vert.x functionality as a library in your own Java or Groovy application, then you can do this too.

Just use the jar `vertx-core-<version>.jar` which is available in the `lib` directory in the distribution.

You then have full access to the core vert.x API, in either Java or Groovy. If you use vert.x embedded you don't have to worry about verticles or any of the deployment related topics, and can just use the core API directly.

Here's an example of a simple embedded web server in Java:

    Vertx vertx = Vertx.newVertx();
    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
        public void handle(HttpServerRequest req) {
            String file = req.path.equals("/") ? "index.html" : req.path;
            req.response.sendFile("webroot/" + file);
        }
    }).listen(8080);
    
And here's the same server in Groovy:

    def vertx = Vertx.newVertx()
    vertx.createHttpServer().requestHandler { req ->
        def file = req.uri == "/" ? "index.html" : req.uri
        req.response.sendFile "webroot/$file"
    }.listen(8080)   
    
It's as simple as that!   

The api documentation (JavaDoc and GroovyDoc) is [here](http://vertx.io/docs.html). Also please take a look at the core manual for Java or Groovy to see full documentation on how to use the API.

## Concepts in vert.x

In this section I'll give an overview of the main concepts in vert.x. Many of these concepts will be discussed in more depth later on in this manual.

### Verticle

The unit of deployment in vert.x is called a *verticle* (think of a particle, for vert.x). Verticles can currently be written in JavaScript, Ruby, Java, or Groovy (we're looking to support Clojure, Scala and Python going ahead).

A verticle is defined by having a *main* which is just the script (or class in the case of Java) to run to start the verticle. A verticle can also contain other scripts which are referenced from the main. It can also contain any jars, and other resources that are used by the verticle.

When you write an application, you might write a single verticle, or your application could consist of a whole set of verticles communicating with each other using the event bus.

### Vert.x Instances

Verticles run inside a vert.x *instance*. A single vert.x instance runs inside its own JVM instance. There can be many verticles running inside a single vert.x instance at any one time. Vert.x makes sure each verticle is isolated by giving it its own classloader so they can't interact by sharing static members, global variables or other means.

There can be many vert.x instances running on the same host, or on different hosts on the network at the same time. The instances can be configured to cluster with each other forming a distributed event bus over which verticle instances can communicate.

### Polyglot

We want you to be able to develop your verticles in a choice of programming languages. Never have developers had such a choice of great languages, and we want that to be reflected in the languages we support. Vert.x allows you to write verticles in JavaScript, Ruby, Java, and Groovy, and we aim to support Clojure, Scala and Python going ahead. These verticles can seamlessly interoperate with other verticles irrespective of what language they are written in.

### Concurrency

A vert.x instance guarantees that a particular verticle instance is always executed by the exact same thread. This gives you a huge advantage as a developer, since you can program all your code as single threaded. Well, that won't be a big deal to you if you are coming from JavaScript where everything is single threaded, but if you're used to multi-threaded programming in Java, Scala, or even Ruby, this may come as a huge relief since you don't have to synchronize access to your state. This means a whole class of race conditions disappear, and OS thread deadlocks are a thing of the past. 

### Event-based Programming Model

Vert.x provides an event-based programming model, similar to frameworks such as node.js.

Most things you do in vert.x involve setting event handlers. For example, to receive data from a TCP socket you set a handler - the handler is then called when data arrives.

You also set handlers to receive messages from the event bus, to receive HTTP requests and responses, to be notified when a connection is closed, or to be notified when a timer fires. There are many examples throughout the vert.x api.

Any other operations in vert.x that don't involve handlers, e.g. writing some data to a socket are guaranteed never to block.

Let's imagine for a minute that the vert.x api allowed a blocking read on a TCP socket. When the code in a verticle called that blocking operation and no data arrived for, say, 1 minute, it means that thread cannot do anything else during that time - it can't do work for any other verticle.

For such a blocking model to work and the system to remain responsive, each verticle instance would need to be assigned its own thread. Now consider what happens when we have thousands, 10s of thousands, or 100s of thousands of verticles running. We clearly can't have that many threads - the overhead due to context switching and stack space would be horrendous. It's clear to see such a blocking model just doesn't scale.

### Event Loops

Internally, a vert.x instance manages a small set of threads, normally matching the number of threads to the available cores on the server. We call these threads *event loops*, since they basically just loop around (well... they do actually go to sleep if there is nothing to do) seeing if there is any work to do, for example, handling some data that's been read from a socket, or executing a timer.

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

### Core and Modules

Vert.x functionality can be conceptually divided into two types: *Core Services*, and *Modules*.

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
* Deploying and undeploying verticles

Vert.x core is fairly static and is not envisaged that it will grow much over time. This is good since every service in core has to be provided in each of the languages we support - that's a lot of API adaptors to write.

#### Modules

Vert.x includes a module system and public module repository.

Vert.x applications or reusable resources can easily be packaged as modules. These modules can be written in any of the languages that Vert.x supports, and they communicate with each other over the event bus by sending and receiving JSON messages.

Since all communication is over the event bus, and not by direct calls like with vert.x core, it means it can be written once in any language, and any verticle in any other language can immediately use it without an API adaptor having to be written for each language.

Whilst vert.x core is fairly static, we envisage that a wide range of modules will be available in vert.x for performing common operations. The Vert.x repository already contains several modules including a persistor, a mailer and work queues.

We also encourage the community to create and contribute their own modules for others to use.

For more information on modules please see the modules manual.

# Interacting with vert.x

The `vertx` command is used to interact with vert.x from the command line. It's main use is to run vert.x verticles.

If you just type `vertx` at a command line you can see the different options the command takes.

## Running Verticles directly

The command `vertx run` is used to start a vert.x verticle.

At minimum `vertx run` takes a single parameter - the name of the main or module to run.

If you're running a local verticle written in JavaScript, Ruby, or Groovy then it's just the name of the script, e.g. `server.js`, `server.rb`, or `server.groovy`. (It doesn't have to be called `server`, you can name it anything as long as it has the right extension). If the verticle is written in Java the name can either be the fully qualified class name of the Main class, *or* you can specify the Java Source file directly and Vert.x will compile it for you.

The `vertx run` command can take a few optional parameters, they are:

* `-conf <config_file>` Provide some configuration to the verticle. `config_file` is the name of a text file containing a JSON object that represents the configuration for the verticle. This is optional.

* `-cp <path>` The path on which to search for the main and any other resources used by the verticle. This defaults to `.` (current directory). If your verticle references other scripts, classes or other resources (e.g. jar files) then make sure these are on this path. The path can contain multiple path entries separated by `:` (colon). Each path entry can be an absolute or relative path to a directory containing scripts, or absolute or relative filenames for jar or zip files.
    An example path might be `-cp classes:lib/otherscripts:jars/myjar.jar:jars/otherjar.jar`
    Always use the path to reference any resources that your verticle requires. Please, **do not** put them on the system classpath as this can cause isolation issues between deployed verticles.
    
* `-instances <instances>` The number of instances of the verticle to instantiate in the vert.x server. Each verticle instance is strictly single threaded so to scale your application across available cores you might want to deploy more than one instance. If omitted a single instance will be deployed. We'll talk more about scaling later on in this user manual.

* `-includes <mod_list>` A comma separated list of module names to include in the classpath of this verticle.
For more information on what including a module means please see the modules manual.

* `-worker` This options determines whether the verticle is a worker verticle or not. Default is false (not a worker). This is discussed in detail later on in the manual.  

* `-cluster` This option determines whether the vert.x server which is started will attempt to form a cluster with other vert.x instances on the network. Clustering vert.x instances allows vert.x to form a distributed event bus with other nodes. Default is false (not clustered).

* `-cluster-port` If the `cluster` option has also been specified then this determines which port will be used for cluster communication with other vert.x instances. Default is `25500`. If you are running more than one vert.x instance on the same host and want to cluster them, then you'll need to make sure each instance has its own cluster port to avoid port conflicts.

* `-cluster-host` If the `cluster` option has also been specified then this determines which host address will be used for cluster communication with other vert.x instances. By default it will try and pick one from the available interfaces. If you have more than one interface and you want to use a specific one, specify it here.

Here are some examples of `vertx run`:

Run a JavaScript verticle server.js with default settings

    vertx run server.js
    
Run 10 instances of a pre-compiled Java verticle specifying classpath

    vertx run com.acme.MyVerticle -cp "classes:lib/myjar.jar" -instances 10
    
Run 10 instances of a Java verticle by source file

    vertx run MyVerticle.java -instances 10    
    
Run 20 instances of a ruby worker verticle    
    
    vertx run order_worker.rb -instances 20 -worker
    
Run two JavaScript verticles on the same machine and let them cluster together with each other and any other servers on the network
    
    vertx run handler1.js -cluster
    vertx run handler1.js -cluster -cluster-port 25501
    
Run a Ruby verticle passing it some config

    vertx run my_vert.rb -conf my_vert.conf
    
Where `my_vert.conf` might contain something like:

    {
        "name": "foo",
        "num_widgets": 46
    }    
    
The config will be available inside the verticle via the core API.    

       
## Running modules from the command line

While you can run a verticle directly from the command line using `vertx run` this can be kind of fiddly since you have to specify the classpath every time.

An alternative is to package your application as a *module*. For detailed information on how to package your code as a module please see the modules manual.

Once you've packaged your module and installed it in the module directory, you can run a module directly from the command line in a similar way to `vertx run`.

Instead of `vertx run` you use `vertx runmod <module name>`. This takes some of the same options as `vertx run`. They are:

* `-conf <config_file>`

* `-instances <instances>`

* `-cluster`

* `-cluster-port`

* `-cluster-host`

They have the exact same meanings as the corresponding options in `vertx run`.

If you attempt to run a module and it hasn't been installed, Vert.x will attempt to install it from the module repository. See the modules manual for more on this.

Some examples of running modules directly:

    Run an installed module called `com.acme.my-mod-v2.1`

    vertx run com.acme.my-mod-v2.1
    
Run an installed module called `com.acme.other-mod-v1.0.beta1` specifying number of instances and some config

    vertx run com.acme.other-mod-v1.0.beta1 -instances 10 -conf other-mod.conf
    
Run the module `vertx.mongo-persistor-v1.0`

    vertx run vertx.mongo-persistor-v1.0     
    
# Logging

Each verticle gets its own logger which can be retrieved from inside the verticle. For information on how to get the logger please see the core guide for the language you are using.

The log files by default go in a file called `vertx.log` in the system temp directory. On my Linux box this is `\tmp`.

By default [JUL](http://docs.oracle.com/javase/7/docs/technotes/guides/logging/overview.html) logging is used. This can be configured using the file `$VERTX_HOME\conf\logging.properties`. Where `VERTX_HOME` is the directory in which you installed vert.x.

Advanced note: If you'd rather use a different logging framework, e.g. log4j you can do this by specifying a system property when running vert.x (edit the vertx.sh script), e.g.

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.impl.Log4jLogDelegateFactory
    
or

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.impl.SLF4JLogDelegateFactory    
    
# Configuring clustering

To configure clustering use the file `conf/cluster.xml` in the distribution.

If you want to receive more info on cluster setup etc, then edit `conf/logging.properties` to read `com.hazelcast.level=INFO`

In particular when running clustered, and you have more than one network interface to choose from, make sure Hazelcast is using the correct interface by editing the `interfaces-enabled` element.

# Performance Tuning

## Improving connection time

If you're creating a lot of connections to a Vert.x server in a short period of time, you need to tweak some settings in order to avoid the TCP accept queue getting full which can result in connections being refused or packets being dropped during the handshake which can cause the client to retry.

A classic symptom of this is if you see long connection times just over 3000ms at your client.#

In Linux you need to increase a couple of settings in the TCP / Net config (10000 is an arbitrarily large number)

    sudo sysctl -w net.core.somaxconn=10000
    sudo sysctl -w net.ipv4.tcp_max_syn_backlog=10000

And you also need to set the accept backlog in your server code, (e.g. in Java:)

    HttpServer server = vertx.createHttpServer();
    server.setAcceptBacklog(10000);


# Internals

Vert.x uses the following amazing open source projects:

* [Netty](https://github.com/netty/netty) for much of its network IO
* [JRuby](http://jruby.org/) for its Ruby engine
* [Groovy](http://groovy.codehaus.org/)
* [Mozilla Rhino](http://www.mozilla.org/rhino/) for its JavaScript engine
* [Jython](http://jython.org) for its Python engine
* [Hazelcast](http://www.hazelcast.com/) for group management of cluster members




    














        
    


    
    



    




    
    

         
       






