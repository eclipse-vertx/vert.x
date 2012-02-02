# User Manual  
   
## Introduction 
   
### What is vert.x? 
      
vert.x is a framework for creating scalable, concurrent, real-time, web-enabled applications.  

Some key features:

* Polyglot. Write your application in JavaScript, Java, Ruby or Groovy. It's up to you. Or mix and match several programming languages in a single application.

* No more worrying about concurrency. vert.x allows you to write all your code as single threaded. Yet, still allows you to scale your applicatin across available cores.

* Has an elegant event based API

### Why vert.x ?

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

### Install it

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

This is the simplest way to run a verticle.

Open a text editor and the type the following into it:

    load('core/vertx.js')

    var server = new vertx.NetServer();

    server.connectHandler(function(sock) {
      new vertx.Pump(sock, sock).start();
    })

    server.listen(1234, 'localhost');

    function vertxStop() {
      server.close
    }
    
It's a very simple TCP echo server, written in JavaScript, and it's your first verticle. Save it in a file called `server.js` somewhere convenient, e.g. in a directory off your home directory.

Now open a console and run the verticle as follows:

    tim@Ethel:~$ 
    tim@Ethel:~$ cd example
    tim@Ethel:~/example$ vertx run server.js  

Now, go to another console, and telnet to it at `localhost`, port `1234`. Type some text (hitting new-line) and you should see the text echoed back to you:

    tim@Ethel:~/example$ telnet localhost 1234
    Trying ::1...
    Trying 127.0.0.1...
    Connected to localhost.
    Escape character is '^]'.
    This is some text that hopefully is going to be echoed back!!
    This is some text that hopefully is going to be echoed back!!
    It worked!
    It worked!
    
*Yay, it worked!* You can `CTRL-C` the server to make it exit. 

So what just happened there? The `vertx` command is used to run vert.x verticles as well as start and stop vert.x standalone servers and deploy and undeploy verticles to standalone server (we'll get to the standalone server stuff a bit later on. If you just type `vertx` at a command line you can see all the various different options the command takes.

In particular `vertx run` is used to start a vert.x verticles in *its own instance* of a vert.x server.

At minimum `vertx run` takes a single parameter - the name of the main to run. If it's a JavaScript, Ruby or Groovy verticles then it's just the name of the script, e.g. `server.js`, `server.rb` or `server.groovy` (it doesn't have to be called server, you can name it anything as long as it has the right extension). If it's Java the main is the fully qualified class name of the main classs.

The `vertx run` command can take a few optional parameters, they are:

* `-cp <path>` The path on which to search for the main and any other resources used by the verticle. This defaults to `.` (current directory). If your verticles references other scripts, classes or other resources (e.g. jar files) then make sure these are on this path. The path can contain multiple path entries separated by `:` (colon). Each path entry can be an absolute or relative path to a directory containing scripts, or absolute or relative filenames for jar or zip files.
    An example path might be `-cp classes:lib/otherscripts:jars/myjar.jar:jars/otherjar.jar`
    Always use the path to reference any resources that your verticle requires. Please, **do not** put them on the system classpath as this can cause isolation issues between deployed verticles.
    
* `-instances <instances>` The number of instances of the verticle to instantiate in the vert.x server. Each verticle instance is strictly single threaded so to scale your application across available cores you might want to deploy more than one instance. The number of instances deployed defaults to the number of available cores detected on the server. We'll talk more about scaling, later on in this user manual [LINK].

* `-worker` This options determines whether the verticle is a worker verticle or not. Default is false (not a worker). This is discussed in detail later on in the manual. [LINK]   

* `-cluster` This option determines whether the vert.x server which is started will attempt to form a cluster with other vert.x instances on the network. Clustering vert.x instances allows vert.x to form a distributed event bus with other nodes. Default is false (not clustered). This is discussed in detail in the chapter on clustering [LINK].

* `-cluster-port` If the `cluster` option has also been specified then this determines which port will be used for cluster communication with other vert.x instances. Default is `25500`.

* `-cluster-host` If the `cluster` option has also been specified then this determines which host addresses  will be used for cluster communication with other vert.x instances. Default is `0.0.0.0` (all available interfaces).

### Running a verticle in a standalone vert.x server

Often you may have several verticles that make up your application - you could just start up each one in its own vert.x instance by using a separate `vertx run` for each verticle and have them communicate using the distributed event bus, but it might be a bit wasteful to have each one running in is own JVM instance.

In such cases it often makes sense to start up a standalone vert.x instance and deploy verticles to that instance instead.

This can be done using the `vertx start` and `vertx deploy` commands. Let's do that now, using the same `server.js` we created in the previous section.

#### Starting a Standalone vert.x server

Go to a console and type:

    tim@Ethel:~$ vertx start
    vert.x server started
    
Now you have a vert.x standalone server running!

The `vertx start` command also takes a few optional parameters, some of which have the same meaning as the corresponding parameter in the `vertx run` command:

* `-port` This specifies which port the server will listen to for deployments. Default is `25571`. You'd want to change this if you had multiple standalone vert.x servers running on the same host. 

* `-cluster` This has the same meaning of the `-cluster` parameter in `vertx run`. 

* `-cluster-port` This has the same meaning of the `-cluster-port` parameter in `vertx run`. 

* `-cluster-host` This has the same meaning of the `-cluster-host` parameter in `vertx run`. 

#### Deploying a Verticle to a Standalone server

Go to another console and deploy the verticle to the standalone server:

    cd example
    tim@Ethel:~/example$ vertx deploy server.js 
    Deploying application name: app-f77e0f95-c4b2-4932-81b8-7dd291a25972 instances: 6
    OK
    
*Ta da!* The verticle is now deployed. If you don't believe me `telnet localhost 1234` once again to make sure.

Any verticles deployed to a standalone server are transient and only last as long as the server is running. You can have as many verticles as you like (subject to available RAM) deployed in the same instance at the same time. The verticles can be in a mixture of languages. Each verticle instance runs in its own classloader so is isolated from any other verticles running in the instance at the same time. You'll hear about verticle can communicate with each other using shared data and the event bus later on.

The `vertx deploy` command can take a few optional parameters, (some have the same meaning as the parameter with the same name in `vertx run`), they are:

* `-name`. A name to give the deployment. This is subsequently used when undeploying the deployment. If you don't specify a name one will be generated for you, and displayed when you deploy.

* `-port` This specifies which port it will attempt to connect to the server on, to deploy the verticle. Default is `25571`.  

* `-cp <path>` This has the same meaning of the `-cp` parameter in `vertx run`.
    
* `-instances <instances>` This has the same meaning of the `-instances` parameter in `vertx run`.

* `-worker` This has the same meaning of the `-worker` parameter in `vertx run`.


#### Undeploying Verticles

To undeploy Verticles previously deployed using `vertx deploy` you simply type: `vertx undeploy <name>`, where `name` is the name the deployment that you specified using the `-name` parameter, or was generated for you, at deployment time.

    tim@Ethel:~/example$ vertx undeploy app-f49609fb-927c-43ef-8dda-34ed174c066f
    OK
    
Job done.    
    
#### Stopping a Standalone vert.x server

Again, easy as pie:

    tim@Ethel:~$ vertx stop
    Stopped vert.x server
    
This will cause the server to exit.

The `vertx stop` command can take an optional parameter:

* `-port` This specifies which port it will attempt to connect to the server on, to stop the server. Default is `25571`. 

## The vert.x Model.

In this section I'd like to give a brief overview of what the different conceptual part of vert.x are and how they hang together.

### Verticle

The component of deployment in vert.x is called a *verticle* (Like a particle, but in vert.x). Verticles can be written in JavaScript, Ruby, Java or Groovy. A verticle is defined by having a *main* which is just the script (or class in the case of Java) to run to start the verticle.

Your application may contain just one verticle, or it could consist of a whole set of verticles communicating with each other using the event bus.

Verticles run inside *vert.x server instance*. 

### Vert.x Instances

Verticles run inside vert.x instances. A single vert.x instance is basically an operating system process running a JVM. There can be many verticle instances running inside a single vert.x instance at any time. vert.x makes sure each vert.x instance is isolated by giving it its own classloader so they can't interact by sharing static members, global variables or other means.

### Concurrency

The vert.x instance guarantees that a particular verticle instance is always executed by the exact same thread. This gives you a huge advantage as a developer, since you can program all your code as single threaded. Well, that won't be a big deal to you if you are coming from JavaScript where everything is single threaded, but if you're used to multi-threaded programming in Java, Scala, or even Ruby, this may come as a huge relief!

Gone are the days of worrying about race conditions, locks, mutexes, volatile variables and synchronization. 

### Event Loops

Internally, the vert.x instance manages a small set of threads, typically matching the number of threads to the available cores on the server. We call these threads event loops, since they basically just loop around (well... they do actually go to sleep if there is nothing to do) seeing if there is any work to do, e.g. reading some data from a socket, or executing a timer.

When a verticle instance is deployed, the server chooses an event loop which will be assigned to that instance. Any subsequent work to be done for that instance will always be dispatched using that thread. Of course, since there are potentially many thousands of verticles running at any one time, a single event loop is assigned to many verticles at the same time.

### Event-based Programming Model

Most things you do in vert.x involve setting event handlers. E.g. to receive data from a TCP socket you set a handler - the handler is then called when data arrives. You also set handlers to receive messages from the event bus, to receive HTTP requests and responses, to be notified when a connection is closed, or to be notified when a timer fires. There are many examples throughout the vert.x api.

In other words, vert.x provides an event-based programming model. 

Any other operations in vert.x that don't involve handlers, e.g. writing some data to a socket are guaranteed never to block.

Why is it done this way?

The answer is: If we want our application to scale with many connections, we don't have a choice.

Let's imagine that the vert.x api allowed a blocking read on a TCP socket. When the code in a verticle called that blocking operation and no data arrived for, say, 1 minute, it means that thread cannot do anything else during that time - it can't do work for any other verticle.

For such a blocking model to work and the system to remain responsive, each verticle instance would need to be assigned its own thread. Now consider what happens when we have thousands, 10s of thousands, or 100s of thousands of verticles running. We clearly can't have that many threads - the overhead due to context switching and stack space would be horrendous. It's clear to see such a blocking model just doesn't scale.

The only way to make it scale is have a 100% non blocking api. There are two ways to do this:

* Used an asynchronous, event based API. Let the system call you when events occur. Do not block waiting for things to happen.

* Use some kind of co-routine approach. Co-routines allow you to suspend the execution of a piece of code and come back to a later when an event occurs. However co-routines are not currently supported across the different languages, or versions of languages that we support in vert.x

vert.x currently takes the event-based api approach. As support for coroutines in various languages matures we will consider also supporting a co-routine based approach in the api.

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









        
    


    
    



    




    
    

         
       






