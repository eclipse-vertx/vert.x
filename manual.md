<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Introduction 
   
## What is Vert.x? 
      
**Vert.x is the ideal platform for the next generation of asynchronous, effortlessly scalable, concurrent applications.**

Vert.x is an event-driven application platform that runs on the JVM.
      
Some of the key highlights include:

* Polyglot. Write your application components in JavaScript, Ruby, Groovy, Java or Python. It's up to you. Or mix and match several programming languages in a single application.

* Super simple concurrency model. Vert.x allows you to write all your code as single threaded, freeing you from the hassle of multi-threaded programming. (No more `synchronized`, `volatile` or explicit locking). 

* Vert.x takes advantage of the JVM and scales seamlessly over available cores without having to manually fork multiple servers and handle inter process communication between them.

* Vert.x has a simple, asynchronous programming model for writing truly scalable non-blocking applications.

* Vert.x includes a distributed event bus that spans the client and server side so your applications components can communicate incredibly easily. The event bus even penetrates into in-browser JavaScript allowing you to create effortless so-called *real-time* web applications.

* Vert.x provides real power and simplicity, without being simplistic. No more sprawling xml configuration files.

* Vert.x includes a module system and public module registry, so you can easily re-use and share Vert.x modules with others.

*Future applications will largely be running on mobile and embedded devices. These demand a platform that can scale with 10s, 100s or even millions of concurrent connections, and allow developers to write scalable, performant applications for them incredibly easily, in whatever language they prefer.*

## Concepts in Vert.x

In this section we'll give an overview of the main concepts in Vert.x. Many of these concepts will be discussed in more depth later on in this manual.

<a id="verticle"> </a>

### Verticle

The packages of code that Vert.x executes are called *verticles* (think of a particle, for Vert.x).

Verticles can be written in JavaScript, Ruby, Java, Groovy or Python (we're looking to support Clojure, Scala and other languages before long).

Many verticles can be executing concurrently in the same Vert.x instance.

An application might be composed of multiple verticles deployed on different nodes of your network communicating by exchanging messages over the Vert.x event bus.

For trivial applications verticles can run directly from the command line, but more usually they are packaged up into modules.

### Module

Vert.x applications are usually composed of one or more modules. Modules can contain multiple verticles, potentially written in different languages. Modules allow functionality to encapsulated and reused. Modules can be placed into any Maven or [Bintray](http://bintray.com) repository, and registered in the Vert.x [module registry](https://vertxmodulereg-vertxmodulereg.rhcloud.com/).

With the Vert.x module system we wish to encourage an eco-system of Vert.x modules managed by the Vert.x community.

For more information on modules, please consult the [Modules manual](mods_manual.html).

### Vert.x Instances

Verticles run inside a Vert.x *instance*. A single Vert.x instance runs inside its own JVM instance. There can be many verticles running inside a single Vert.x instance at any one time. 

There can be many Vert.x instances running on the same host, or on different hosts on the network at the same time. The instances can be configured to cluster with each other forming a distributed event bus over which verticle instances can communicate.

### Polyglot

We want you to be able to develop your verticles in a choice of programming languages. Never have developers had such a choice of great languages, and we want that to be reflected in the languages we support. Vert.x allows you to write verticles in JavaScript, Ruby, Java, Groovy and Python and we aim to support Clojure and Scala before long. These verticles can seamlessly interoperate with other verticles irrespective of what language they are written in.

### Concurrency

A Vert.x instance guarantees that a particular verticle instance is never executed by more than one thread concurrently. This gives you a huge advantage as a developer, since you can program all your code as single threaded. If you're used to traditional this may come as a huge relief since you don't have to synchronize access to your state. This means a whole class of race conditions disappear, and OS thread deadlocks are a thing of the past. 

### Asynchronous Programming Model

At the core, Vert.x provides an asychronous programming model. This means that most things you do in Vert.x involve setting event handlers. For example, to receive data from a TCP socket you set a handler - the handler is then called when data arrives.

You also set handlers to receive messages from the event bus, to receive HTTP requests and responses, to be notified when a connection is closed, or to be notified when a timer fires. There are many examples throughout the Vert.x api.

We use an asynchronous API so that we can scale to handle many verticles using a small number of operating system threads. In fact Vert.x sets the number of threads to be equal to the number of available cores on the machine. With a prefectly non blocking application you would never need any more threads than that.

With a traditional synchronous API, threads block on API operations, and while they are blocked they cannot do any other work. A good example is a blocking read from a socket. While code is waiting for data to arrive on a socket it cannot do anything else. This means that if we want to support 1 million concurrent connections (not a crazy idea for the next generation of mobile applications) then we would need 1 million threads. This approach clearly doesn't scale.

Asynchronous APIs are sometimes criticised as being hard to develop with, especially when you have to co-ordinate results from more than one event handler. The recommended approach to deal with this in Vert.x is to use a module such as [mod-rx-vertx](https://github.com/vert-x/mod-rxvertx) which allows you to compose asynchronous event streams in powerful ways. `mod-rx-vertx` uses the great [RxJava](https://github.com/Netflix/RxJava) library which is inspired from the .net ["Reactive extensions"](http://msdn.microsoft.com/en-us/data/gg577609.aspx).

### Event Loops

Internally, a Vert.x instance manages a small set of threads, matching the number of threads to the available cores on the server. We call these threads *event loops*, since they basically just loop around (well... they do actually go to sleep if there is nothing to do) seeing if there is any work to do, for example, handling some data that's been read from a socket, or executing a timer.

When a standard verticle instance is deployed, the server chooses an event loop which will be assigned to that instance. Any subsequent work to be done for that instance will always be dispatched using that thread. Of course, since there are potentially many thousands of verticles running at any one time, a single event loop is assigned to many verticles at the same time.

We call this the *multi-reactor pattern*. It's like the [reactor pattern](http://en.wikipedia.org/wiki/Reactor_pattern) but there's more than one event loop.

#### The Golden Rule - Don't block the event loop!

A particular event loop is used to service potential many verticle instances, so it's critical that you don't block it in your standard verticle. If you block it it means it can't service handlers for any other verticle instance, and your application will grind to a halt.

Blocking an event loop means doing anything that ties up the event loop in the verticle and doesn't allow it to quickly continue to handle other events, this includes:

* `Thread.sleep()`
* Blocking on any other object (e.g. `Object.wait()`)
* Spinning in a loop
* Executing a long-lived computationally intensive operation
* Calling a blocking third party library operation that might take some time to complete (e.g. executing a JDBC query)

### Worker Verticles

In a standard verticle you should never block the event loop, however there are cases where you really can't avoid blocking, or you genuinely have computationally intensive operations to perform.

An example would calling a "traditional" Java API like JDBC.

For cases like these, Vert.x allows you to mark a particular verticle instance as a *worker verticle*. A worker verticle differs from a standard verticle in that it is not assigned a Vert.x event loop thread, instead it executes on a thread from an internal thread pool called the *background pool*. 

Worker verticles are never executed concurrently by more than one thread. Worker verticles should be kept to a minimum, since a blocking approach doesn't scale if you want to deal with many concurrent connections.

### Message Passing

Verticles can communicate with other verticles running in the same, or different Vert.x instance using the event bus. If you think of each verticle as an actor, this in some ways resembles the [actor model](http://en.wikipedia.org/wiki/Actor_model) as popularised by the Erlang programming language.

By having many verticle instances in a Vert.x server instance and allowing message passing allows the system to scale well over available cores without having to allow multi-threaded execution of any verticle code. 

### Shared data

Message passing is extremely useful, but it's not always the best approach to concurrency for all types of applications. Some use cases are better solved by providing shared data structures that can be accessed directly by different verticle instances in the same Vert.x instance. 

Vert.x provides a shared map and shared set facility. We insist that the data stored is *immutable* in order to prevent race conditions that might occur if concurrent access to shared state was allowed.

### Vert.x APIs

Vert.x provides a small and fairly static set of APIs that can be called directly from verticles. We provide the APIs in each of the languages that Vert.x supports.

We envisage that the Vert.x APIs don't change much over time and new functionality is added by the community and the Vert.x core team in the form of Vert.x modules which can be published and re-used by anyone.

The Vert.x APIs can be divided in the *container API* and the *core API*

#### Container API

This is the verticle's view of the Vert.x container in which it is running. It contains operations to do things like:

* Deploy and undeploy verticles
* Deploy and undeploy modules
* Logging
* Retrieve verticle configuration

#### Core API

This API allows:

* TCP/SSL servers and clients
* HTTP/HTTPS servers and clients
* WebSockets servers and clients
* Accessing the distributed event bus
* Periodic and one-off timers
* Buffers
* Flow control
* Accessing files on the file system
* Shared map and sets
* Accessing configuration
* SockJS

<a id="running-vertx"> </a>

# Using Vert.x from the command line

The `vertx` command is used to interact with Vert.x from the command line. It's main use is to run Vert.x modules and verticles.

If you just type `vertx` at a command line you can see the different options the command takes.

## Running Verticles directly

You can run raw Vert.x verticles directly from the command line using 'vertx run'.

Running raw verticles is useful for quickly prototyping code or for trivial applications, but for anything non trivial it's highly recommended to package your application as a [module](mods_manual.html) instead. Packaging as module makes the module easier to run, encapsulate and reuse.

At minimum `vertx run` takes a single parameter - the name of the main to run.

If you're running a verticle written in JavaScript, Ruby, Groovy or Python then it's just the name of the script, e.g. `server.js`, `server.rb`, or `server.groovy`. (It doesn't have to be called `server`, you can name it anything as long as it has the right extension). If the verticle is written in Java the name can either be the fully qualified class name of the Main class, *or* you can specify the Java Source file directly and Vert.x will compile it for you.

    vertx run groovy:com.mycompany.MyGroovyMainClass

The `vertx run` command can take a few optional parameters, they are:

* `-conf &lt;config_file&gt;` Provide some configuration to the verticle. `config_file` is the name of a text file containing a JSON object that represents the configuration for the verticle. This is optional.

* `-cp &lt;path&gt;` The path on which to search for the main and any other resources used by the verticle. This defaults to `.` (current directory). If your verticle references other scripts, classes or other resources (e.g. jar files) then make sure these are on this path. The path can contain multiple path entries separated by `:` (colon). Each path entry can be an absolute or relative path to a directory containing scripts, or absolute or relative filenames for jar or zip files.
    An example path might be `-cp classes:lib/otherscripts:jars/myjar.jar:jars/otherjar.jar`
    Always use the path to reference any resources that your verticle requires. Please, **do not** put them on the system classpath as this can cause isolation issues between deployed verticles.
    
* `-instances &lt;instances&gt;` The number of instances of the verticle to instantiate in the Vert.x server. Each verticle instance is strictly single threaded so to scale your application across available cores you might want to deploy more than one instance. If omitted a single instance will be deployed. We'll talk more about scaling later on in this user manual.

* `-includes &lt;mod_list&gt;` A comma separated list of module names to include in the classpath of this verticle.
For more information on what including a module means please see the modules manual.

* `-worker` This options determines whether the verticle is a worker verticle or not. Default is false (not a worker). This is discussed in detail later on in the manual.  

* `-cluster` This option determines whether the Vert.x server which is started will attempt to form a cluster with other Vert.x instances on the network. Clustering Vert.x instances allows Vert.x to form a distributed event bus with other nodes. Default is false (not clustered).

* `-cluster-port` If the `cluster` option has also been specified then this determines which port will be used for cluster communication with other Vert.x instances. Default is `0` -which means 'chose a free ephemeral port. You don't usually need to specify this parameter unless you really need to bind to a specific port.

* `-cluster-host` If the `cluster` option has also been specified then this determines which host address will be used for cluster communication with other Vert.x instances. By default it will try and pick one from the available interfaces. If you have more than one interface and you want to use a specific one, specify it here.

Here are some examples of `vertx run`:

Run a JavaScript verticle server.js with default settings

    vertx run server.js
    
Run 10 instances of a pre-compiled Java verticle specifying classpath

    vertx run com.acme.MyVerticle -cp "classes:lib/myjar.jar" -instances 10
    
Run 10 instances of a Java verticle by *source file*

    vertx run MyVerticle.java -instances 10    
    
Run 20 instances of a ruby worker verticle    
    
    vertx run order_worker.rb -instances 20 -worker
    
Run two JavaScript verticles on the same machine and let them cluster together with each other and any other servers on the network
    
    vertx run handler.js -cluster
    vertx run sender.js -cluster

Run a Ruby verticle passing it some config

    vertx run my_vert.rb -conf my_vert.conf
    
Where `my_vert.conf` might contain something like:

    {
        "name": "foo",
        "num_widgets": 46
    }    
    
The config will be available inside the verticle via the core API.    

### Forcing language implementation to use

Vert.x works out what language implementation module to use based on the file prefix using the mapping in the file `langs.properties` in the Vert.x distribution. If there is some ambiguity, e.g. you want to specify a class as a main, but it's a Groovy class, not a Java class, then you can prefix the main with the language implementation name, e.g. to run a compiled class as a Groovy verticle:

    vertx run groovy:com.mycompany.MyGroovyMainVerticle

       
## Running modules from the command line

It's highly recommended that you package any non trivial Vert.x functionality into a module. For detailed information on how to package your code as a module please see the [modules manual](mods_manual.html).

Instead of `vertx run` you use `vertx runmod &lt;module name&gt;` to directly run a module. This takes some of the same options as `vertx run`. They are:

* `-conf &lt;config_file&gt;`

* `-instances &lt;instances&gt;`

* `-cluster`

* `-cluster-host`

* `-cp` If this option is specified for a *module* then it overrides the standard module classpath and Vert.x will search for the `mod.json` and other module resources using the specified classpath instead. This can be really useful when, for example, developing a module in an IDE - you can run the module in a different classpath and specify the classpath to point to where the idea stores the project resources. Couple this with auto-redeploy of modules and you can have your module immediately reloaded and reflecting the changes in your IDE as you make them.

They have the exact same meanings as the corresponding options in `vertx run`.

If you attempt to run a module and it hasn't been installed locally, then Vert.x will attempt to install it from one of the configured repositories. Out of the box Vert.x is configured to install modules from Maven Central, Sonatype Nexus, Bintray and your local Maven repository. You can also configure it to use any other Maven or bintray repository by configuring the `repos.txt` file in the Vert.x `conf` directory. See the modules manual for more on this.

Some examples of running modules directly:

    Run an module called `com.acme~my-mod~2.1`

    vertx runmod com.acme~my-mod~2.1
    
Run a module called `com.acme~other-mod~1.0.beta1` specifying number of instances and some config

    vertx runmod com.acme~other-mod~1.0.beta1 -instances 10 -conf other-mod.conf

## Running modules directory from .zip files

The command `vert.x runzip` can also be used to run a module directly from a module zip file, i.e. the module doesn't have to be pre-installed either locally or in a module repository somewhere. To do this just type

    vertx runzip &lt;zip_file_name&gt;

For example

   vertx runzip my-mod~2.0.1.zip


## Displaying version of Vert.x

To display the installed version of Vert.x type

    vertx version

## Installing and uninstalling modules

Please see the [modules manual]() for a detailed description of this.
        
<a id="logging"> </a>

# Logging

Each verticle instance gets its own logger which can be retrieved from inside the verticle. For information on how to get the logger please see the core guide for the language you are using.

The log files by default go in a file called `vertx.log` in the system temp directory. On my Linux box this is `\tmp`.

By default [JUL](http://docs.oracle.com/javase/7/docs/technotes/guides/logging/overview.html) logging is used. This can be configured using the file `$VERTX_HOME\conf\logging.properties`. Where `VERTX_HOME` is the directory in which you installed Vert.x.

Advanced note: If you'd rather use a different logging framework, e.g. log4j you can do this by specifying a system property when running Vert.x (edit the vertx.sh script), e.g.

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.impl.Log4jLogDelegateFactory
    
or

    -Dorg.vertx.logger-delegate-factory-class-name=org.vertx.java.core.logging.impl.SLF4JLogDelegateFactory  

If you don't want to use the Vert.x provided logging facilities that's fine. You can just use your preferred logging framework as normal and include the logging jar and config in your module.  
    
# Configuring clustering

To configure clustering use the file `conf/cluster.xml` in the distribution.

If you want to receive more info on cluster setup etc, then edit `conf/logging.properties` to read `com.hazelcast.level=INFO`

In particular when running clustered, and you have more than one network interface to choose from, make sure Hazelcast is using the correct interface by editing the `interfaces-enabled` element.

If your network does not support multicast you can easily disable multicast and enable tcp-ip in the configuration file.

# Performance Tuning

## Improving connection time

If you're creating a lot of connections to a Vert.x server in a short period of time, you may need to tweak some settings in order to avoid the TCP accept queue getting full which can result in connections being refused or packets being dropped during the handshake which can then cause the client to retry.

A classic symptom of this is if you see long connection times just over 3000ms at your client.#

How to tune this is operating system specific but in Linux you need to increase a couple of settings in the TCP / Net config (10000 is an arbitrarily large number)

    sudo sysctl -w net.core.somaxconn=10000
    sudo sysctl -w net.ipv4.tcp_max_syn_backlog=10000

And you also need to set the accept backlog in your server code, (e.g. in Java:)

    HttpServer server = vertx.createHttpServer();
    server.setAcceptBacklog(10000);

## Handling large numbers of connections

### Increase number of available file handles

In order to handle large numbers of connections on your server you will probably have to increase the maximum number of file handles as each socket requires a file handle. How to do this is operating system specific

### Tune TCP buffer size

Each TCP connection allocates memory for its buffer, so to support many connections in limited RAM you may need to reduce the TCP buffer size, e.g.

    HttpServer server = vertx.createHttpServer();
    server.setSendBufferSize(4 * 1024);
    server.setReceiveBufferSize(4 * 1024);

# Internals

Vert.x uses the following amazing open source projects:

* [Netty](https://github.com/netty/netty) for much of its network IO
* [JRuby](http://jruby.org/) for its Ruby engine
* [Groovy](http://groovy.codehaus.org/)
* [Mozilla Rhino](http://www.mozilla.org/rhino/) for its JavaScript engine
* [Jython](http://jython.org) for its Python engine
* [Hazelcast](http://www.hazelcast.com/) for group management of cluster members




    














        
    


    
    



    




    
    

         
       






