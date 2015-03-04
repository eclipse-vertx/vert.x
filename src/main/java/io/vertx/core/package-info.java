/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

/**
 * = Vert.x Core Manual
 * :toc: left
 *
 * At the heart of Vert.x is a set of Java APIs that we call *Vert.x Core*
 *
 * https://github.com/eclipse/vert.x[Repository].
 *
 * Vert.x core provides functionality for things like:
 *
 * * Writing TCP clients and servers
 * * Writing HTTP clients and servers including support for WebSockets
 * * The Event bus
 * * Shared data - local maps and clustered distributed maps
 * * Periodic and delayed actions
 * * Deploying and undeploying Verticles
 * * Datagram Sockets
 * * DNS client
 * * File system access
 * * High availability
 * * Clustering
 *
 * The functionality in core is fairly low level - you won't find stuff like database access, authorisation or high level
 * web functionality here - that kind of stuff you'll find in *Vert.x ext* (extensions).
 *
 * Vert.x core is small and lightweight. You just use the parts you want. It's also entirely embeddable in your
 * existing applications - we don't force you to structure your applications in a special way just so you can use
 * Vert.x.
 *
 * You can use core from any of the other languages that Vert.x supports. But here'a a cool bit - we don't force
 * you to use the Java API directly from, say, JavaScript or Ruby - after all, different languages have different conventions
 * and idioms, and it would be odd to force Java idioms on Ruby developers (for example).
 * Instead, we automatically generate an *idiomatic* equivalent of the core Java APIs for each language.
 *
 * From now on we'll just use the word *core* to refer to Vert.x core.
 *
 * Let's discuss the different concepts and features in core.
 *
 * == In the beginning there was Vert.x
 *
 * NOTE: Much of this is Java specific - need someway of swapping in language specific parts
 *
 * You can't do much in Vert.x-land unless you can commune with a {@link io.vertx.core.Vertx} object!
 *
 * It's the control centre of Vert.x and is how you do pretty much everything, including creating clients and servers,
 * getting a reference to the event bus, setting timers, as well as many other things.
 *
 * So how do you get an instance?
 *
 * If you're embedding Vert.x then you simply create an instance as follows:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example1}
 * ----
 *
 * If you're using Verticles
 *
 * NOTE: Most applications will only need a single Vert.x instance, but it's possible to create multiple Vert.x instances if you
 * require, for example, isolation between the event bus or different groups of servers and clients.
 *
 * === Specifying options when creating a Vertx object
 *
 * When creating a Vertx object you can also specify options if the defaults aren't right for you:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example2}
 * ----
 *
 * The {@link io.vertx.core.VertxOptions} object has many settings and allows you to configure things like clustering,
 * high availability, pool sizes and various other settings. The Javadoc describes all the settings in detail.
 *
 * === Creating a clustered Vert.x object
 *
 * If you're creating a *clustered Vert.x* (See the section on the <<event_bus, event bus>> for more information
 * on clustering the event bus), then you will normally use the asynchronous variant to create the Vertx object.
 *
 * This is because it usually takes some time (maybe a few seconds) for the different Vert.x instances in a cluster to
 * group together. During that time, we don't want to block the calling thread, so we give the result to you asynchronously.
 *
 * == Are you fluent?
 *
 * You may have noticed that in the previous examples a *fluent* API was used.
 *
 * A fluent API is where multiple methods calls can be chained together. For example:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example3}
 * ----
 *
 * This is a common pattern throughout Vert.x APIs, so get used to it.
 *
 * Chaining calls like this allows you to write code that's a little bit less verbose. Of course, if you don't
 * like the fluent approach *we don't force you* to do it that way, you can happily ignore it if you prefer and write
 * your code like this:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example4}
 * ----
 *
 * == Don't call us, we'll call you.
 *
 * The Vert.x APIs are largely _event driven_. This means that when things happen in Vert.x that you are interested in,
 * Vert.x will call you by sending you events.
 *
 * Some example events are:
 *
 * * a timer has fired
 * * some data has arrived on a socket,
 * * some data has been read from disk
 * * an exception has occurred
 * * an HTTP server has received a request
 *
 * You handle events by providing _handlers_ to the Vert.x APIs. For example to receive a timer event every second you
 * would do:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example5}
 * ----
 *
 * Or to receive an HTTP request:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example6}
 * ----
 *
 * Some time later when Vert.x has an event to pass to your handler Vert.x will call it *asynchronously*.
 *
 * This leads us to some important concepts in Vert.x:
 *
 * == Don't block me!
 *
 * With very few exceptions (i.e. some file system operations ending in 'Sync'), none of the APIs in Vert.x block the calling thread.
 *
 * If a result can be provided immediately, it will be returned immediately, otherwise you will usually provide a handler
 * to receive events some time later.
 *
 * Because none of the Vert.x APIs block threads that means you can use Vert.x to handle a lot of concurrency using
 * just a small number of threads.
 *
 * With a conventional blocking API the calling thread might block when:
 *
 * * Reading data from a socket
 * * Writing data to disk
 * * Sending a message to a recipient and waiting for a reply.
 * * ... Many other situations
 *
 * In all the above cases, when your thread is waiting for a result it can't do anything else - it's effectively useless.
 *
 * This means that if you want a lot of concurrency using blocking APIs then you need a lot of threads to prevent your
 * application grinding to a halt.
 *
 * Threads have overhead in terms of the memory they require (e.g. for their stack) and in context switching.
 *
 * For the levels of concurrency required in many modern applications, a blocking approach just doesn't scale.
 *
 * == Reactor and Multi-Reactor
 *
 * We mentioned before that Vert.x APIs are event driven - Vert.x passes events to handlers when they are available.
 *
 * In most cases Vert.x calls your handlers using a thread called an *event loop*.
 *
 * As nothing in Vert.x or your application blocks, the event loop can merrily run around delivering events to different handlers in succession
 * as they arrive.
 *
 * Because nothing blocks, an event loop can potentially deliver huge amounts of events in a short amount of time.
 * For example a single event loop can handle many thousands of HTTP requests very quickly.
 *
 * We call this the http://en.wikipedia.org/wiki/Reactor_pattern[Reactor Pattern].
 *
 * You may have heard of this before - for example Node.js implements this pattern.
 *
 * In a standard reactor implementation there is a *single event loop* thread which runs around in a loop delivering all
 * events to all handlers as they arrive.
 *
 * The trouble with a single thread is it can only run on a single core at any one time, so if you want your single threaded
 * reactor application (e.g. your Node.js application) to scale over your multi-core server you have to start up and
 * manage many different processes.
 *
 * Vert.x works differently here. Instead of a single event loop, each Vertx instance maintains *several event loops*.
 * By default we choose the number based on the number of available cores on the machine, but this can be overridden.
 *
 * This means a single Vertx process can scale across your server, unlike Node.js.
 *
 * We call this pattern the *Multi-Reactor Pattern* to distinguish it from the single threaded reactor pattern.
 *
 * NOTE: Even though a Vertx instance maintains multiple event loops, any particular handler will never be executed
 * concurrently, and in most cases (with the exception of <<worker_verticles, worker verticles>>) will always be called
 * using the *exact same event loop*.
 *
 * [[golden_rule]]
 * == The Golden Rule - Don't Block the Event Loop
 *
 * We already know that the Vert.x APIs are non blocking and won't block the event loop, but that's not much help if
 * you block the event loop *yourself* in a handler.
 *
 * If you do that, then that event loop will not be able to do anything else while it's blocked. If you block all of the
 * event loops in Vertx instance then your application will grind to a complete halt!
 *
 * So don't do it! *You have been warned*.
 *
 * Examples of blocking include:
 *
 * * +Thread.sleep()+
 * * Waiting on a lock
 * * Waiting on a mutex or monitor (e.g. synchronized section)
 * * Doing a long lived database operation and waiting for a result
 * * Doing a complex calculation that takes some significant time.
 * * Spinning in a loop
 *
 * If any of the above stop the event loop from doing anything else for a *significant amount of time* then you should
 * go immediately to the naughty step, and await further instructions.
 *
 * So... what is a *significant amount of time*?
 *
 * How long is a piece of string? It really depends on your application and the amount of concurrency you require.
 *
 * If you have a single event loop, and you want to handle 10000 http requests per second, then it's clear that each request
 * can't take more than 0.1 ms to process, so you can't block for any more time than that.
 *
 * *The maths is not hard and shall be left as an exercise for the reader.*
 *
 * If your application is not responsive it might be a sign that you are blocking an event loop somewhere. To help
 * you diagnose such issues, Vert.x will automatically log warnings if it detects an event loop hasn't returned for
 * some time. If you see warnings like these in your logs, then you should investigate.
 *
 *  Thread vertx-eventloop-thread-3 has been blocked for 20458 ms
 *
 * Vert.x will also provide stack traces to pinpoint exactly where the blocking is occurring.
 *
 * If you want to turn of these warnings or change the settings, you can do that in the
 * {@link io.vertx.core.VertxOptions} object before creating the Vertx object.
 *
 * [[blocking_code]]
 * == Running blocking code
 *
 * In a perfect world, there will be no war or hunger, all APIs will be written asynchronously and bunny rabbits will
 * skip hand-in-hand with baby lambs across sunny green meadows.
 *
 * *But.. the real world is not like that. (Have you watched the news lately?)*
 *
 * Fact is, many, if not most libraries, especially in the JVM ecosystem have synchronous APIs and many of the methods are
 * likely to block. A good example is the JDBC API - it's inherently asynchronous, and no matter how hard it tries, Vert.x
 * cannot sprinkle magic pixie dust on it to make it asynchronous.
 *
 * We're not going to rewrite everything to be asynchronous overnight so we need to provide you a way to use "traditional"
 * blocking APIs safely within a Vert.x application.
 *
 * As discussed before, you can't call blocking operations directly from an event loop, as that would prevent it
 * from doing any other useful work. So how can you do this?
 *
 * It's done by calling {@link io.vertx.core.Vertx#executeBlocking} specifying both the blocking code to execute and a
 * result handler to be called back asynchronous when the blocking code has been executed.
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example7}
 * ----
 *
 * An alternative way to run blocking code is to use a <<worker_verticles, worker verticle>>
 *
 * == Verticles
 *
 * Vert.x comes with a simple, scalable, _actor-like_ deployment and concurrency model out of the box that
 * you can use to save you writing your own.
 *
 * *This model is entirely optional and Vert.x does not force you to create your applications in this way if you don't
 * want to.*.
 *
 * The model does not claim to be a strict actor-model implementation, but it does share similarities especially
 * with respect to concurrency, scaling and deployment.
 *
 * To use this model, you write your code as set of *verticles*.
 *
 * Verticles are chunks of code that get deployed and
 * run by Vert.x. Verticles can be written in any of the languages that Vert.x supports and a single application
 * can include verticles written in multiple languages.
 *
 * You can think of a verticle as a bit like an actor in the http://en.wikipedia.org/wiki/Actor_model[Actor Model].
 *
 * An application would typically be composed of many verticle instances running in the same Vert.x instance at the same
 * time. The different verticle instances communicate with each other by sending messages on the <<event_bus, event bus>>.
 *
 * include::override/verticles.adoc[]
 *
 * === Verticle Types
 *
 * There are three different types of verticles:
 *
 * Standard Verticles:: These are the most common and useful type - they are always executed using an event loop thread.
 * We'll discuss this more in the next section.
 * Worker Verticles:: These run using a thread from the worker pool. An instance is never executed concurrently by more
 * than one thread.
 * Multi-threaded worker verticles:: These run using a thread from the worker pool. An instance can be executed concurrently by more
 * than one thread.
 *
 * === Standard verticles
 *
 * Standard verticles are assigned an event loop thread when they are created and the +start+ method is called with that
 * event loop. When you call any other methods that takes a handler on a core API from an event loop then Vert.x
 * will guarantee that those handlers, when called, will be executed on the same event loop.
 *
 * This means we can guarantee that all the code in your verticle instance is always executed on the same event loop (as
 * long as you don't create your own threads and call it!).
 *
 * This means you can write all the code in your application as single threaded and let Vert.x worrying about the threading
 * and scaling. No more worrying about +synchronized+ and +volatile+ any more, and you also avoid many other cases of race conditions
 * and deadlock so prevalent when doing hand-rolled 'traditional' multi-threaded application development.
 *
 * [[worker_verticles]]
 * === Worker verticles
 *
 * A worker verticle is just like a standard verticle but it's executed not using an event loop, but using a thread from
 * the Vert.x worker thread pool.
 *
 * Worker verticles are designed for calling blocking code, as they won't block any event loops.
 *
 * If you don't want to use a worker verticle to run blocking code, you can also run <<blocking_code, inline blocking code>>
 * directly while on an event loop.
 *
 * If you want to deploy a verticle as a worker verticle you do that with {@link io.vertx.core.DeploymentOptions#setWorker}.
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example7_1}
 * ----
 *
 * Worker verticle instances are never executed concurrently by Vert.x by more than one thread, but can executed by
 * different threads at different times.
 *
 * ==== Multi-threaded worker verticles
 *
 * A multi-threaded worker verticle is just like a normal worker verticle but it *can* be executed concurrently by
 * different threads.
 *
 * WARNING: Multi-threaded worker verticles are an advanced feature and most applications will have no need for them.
 * Because of the concurrency in these verticles you have to be very careful to keep the verticle in a consistent state
 * using standard Java techniques for multi-threaded programming.
 *
 * === Deploying verticles programmatically
 *
 * You can deploy a verticle using one of the {@link io.vertx.core.Vertx#deployVerticle} method, specifying a verticle
 * name or you can pass in a verticle instance you have already created yourself.
 *
 * NOTE: Deploying Verticle *instances* is Java only.
 *
 * [source,java]
 * ----
 * {@link examples.CoreExamples#example8}
 * ----
 *
 * You can also deploy verticles by specifying the verticle *name*.
 *
 * The verticle name is used to look up the specific {@link io.vertx.core.spi.VerticleFactory} that will be used to
 * instantiate the actual verticle instance(s).
 *
 * Different verticle factories are available for instantiating verticles in different languages and for various other
 * reasons such as loading services and getting verticles from Maven at run-time.
 *
 * This allows you to deploy verticles written in any language from any other language that Vert.x supports.
 *
 * Here's an example of deploying some different types of verticles:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example9}
 * ----
 *
 * === Rules for mapping a verticle name to a verticle factory
 *
 * When deploying verticle(s) using a name, the name is used to select the actual verticle factory that will instantiate
 * the verticle(s).
 *
 * Verticle names can have a prefix - which is a string followed by a colon, which if present will be used to look-up the factory, e.g.
 *
 *  js:foo.js // Use the JavaScript verticle factory
 *  groovy:com.mycompany.SomeGroovyCompiledVerticle // Use the Groovy verticle factory
 *  service:com.mycompany:myorderservice // Uses the service verticle factory
 *
 * If no prefix is present, Vert.x will look for a suffix and use that to lookup the factory, e.g.
 *
 *  foo.js // Will also use the JavaScript verticle factory
 *  SomeScript.groovy // Will use the Groovy verticle factory
 *
 * If no prefix or suffix is present, Vert.x will assume it's a Java fully qualified class name (FQCN) and try
 * and instantiate that.
 *
 * === How are Verticle Factories located?
 *
 * Most Verticle factories are loaded from the classpath and registered at Vert.x startup.
 *
 * You can also programmatically register and unregister verticle factories using {@link io.vertx.core.Vertx#registerVerticleFactory}
 * and {@link io.vertx.core.Vertx#unregisterVerticleFactory} if you wish.
 *
 * === Waiting for deployment to complete
 *
 * Verticle deployment is asynchronous and may complete some time after the call to deploy has returned.
 *
 * If you want to be notified when deployment is complete you can deploy specifying a completion handler:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example10}
 * ----
 *
 * The completion handler will be passed a result containing the deployment ID string, if deployment succeeded.
 *
 * This deployment ID can be used later if you want to undeploy the deployment.
 *
 * === Undeploying verticle deployments
 *
 * Deployments can be undeployed with {@link io.vertx.core.Vertx#undeploy}.
 *
 * Un-deployment is itself asynchronous so if you want to be notified when un-deployment is complete you can deploy specifying a completion handler:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example11}
 * ----
 *
 * === Specifying number of verticle instances
 *
 * When deploying a verticle using a verticle name, you can specify the number of verticle instances that you
 * want to deploy:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example12}
 * ----
 *
 * This is useful for scaling easily across multiple cores. For example you might have a web-server verticle to deploy
 * and multiple cores on your machine, so you want to deploy multiple instances to take utilise all the cores.
 *
 * === Passing configuration to a verticle
 *
 * Configuration in the form of JSON can be passed to a verticle at deployment time:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example13}
 * ----
 *
 * This configuration is then available via the {@link io.vertx.core.Context} object.
 *
 * TODO
 *
 *
 * === Accessing environment variables in a Verticle
 *
 * TODO
 *
 * === Verticle Isolation Groups
 *
 * By default, Vert.x has a _flat classpath_. I.e, it does everything, including deploying verticles without messing
 * with class-loaders. In the majority of cases this is the simplest, clearest and sanest thing to do.
 *
 * However, in some cases you may want to deploy a verticle so the classes of that verticle are isolated from others in
 * your application.
 *
 * This might be the case, for example, if you want to deploy two different versions of a verticle with the same class name
 * in the same Vert.x instance, or if you have two different verticles which use different versions of the same jar library.
 *
 * WARNING: Use this feature with caution. Class-loaders can be a can of worms, and can make debugging difficult, amongst
 * other things.
 *
 * Here's an example of using an isolation group to isolate a verticle deployment.
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example14}
 * ----
 *
 * Isolation groups are identified by a name, and the name can be used between different deployments if you want them
 * to share an isolated class-loader.
 *
 * Extra classpath entries can also be provided with {@link io.vertx.core.DeploymentOptions#setExtraClasspath} so they
 * can locate resources that are isolated to them.
 *
 * === High Availability
 *
 * Verticles can be deployed with High Availability (HA) enabled.
 *
 * TODO
 *
 * === Running Verticles from the command line
 *
 * You can use Vert.x directly in your Maven or Gradle projects in the normal way by adding a dependency to the Vert.x
 * core library and hacking from there.
 *
 * However you can also run Vert.x verticles directly from the command line if you wish.
 *
 * To do this you need to download and install a Vert.x distribution, and add the `bin` directory of the installation
 * to your `PATH` environment variable. Also make sure you have a Java 8 JDK on your `PATH`.
 *
 * You can now run verticles by using the `vertx run` command. Here are some examples:
 *
 * ----
 * # Run a JavaScript verticle
 * vertx run my_verticle.js
 *
 * # Run a Ruby verticle
 * vertx run a_n_other_verticle.rb
 *
 * # Run a Groovy script verticle, clustered
 * vertx run FooVerticle.groovy -cluster
 * ----
 *
 * You can even run Java source verticles without compiling them first!
 *
 * ----
 * vertx run SomeJavaSourceFile.java
 * ----
 *
 * Vert.x will compile the Java source file on the fly before running it. This is really useful for quickly
 * prototyping verticles and great for demos. No need to set-up a Maven or Gradle build first to get going!
 *
 * For full information on the various options available when executing `vertx` on the command line,
 * type `vertx` at the command line.
 *
 * === Causing Vert.x to exit
 *
 * Threads maintained by Vert.x instances are not daemon threads so they will prevent the JVM from exiting.
 *
 * If you are embedding Vert.x and you have finished with it, you can call {@link io.vertx.core.Vertx#close} to close it
 * down.
 *
 * This will shut-down all internal thread pools and close other resources, and will allow the JVM to exit.
 *
 * === The Context object
 *
 * TODO
 *
 * === Executing periodic and delayed actions
 *
 * It's very common in Vert.x to want to perform an action after a delay, or periodically.
 *
 * In standard verticles you can't just make the thread sleep to introduce a delay, as that will block the event loop thread.
 *
 * Instead you use Vert.x timers. Timers can be *one-shot* or *periodic*. We'll discuss both
 *
 * ==== One-shot Timers
 *
 * A one shot timer calls an event handler after a certain delay, expressed in milliseconds.
 *
 * To set a timer to fire once you use {@link io.vertx.core.Vertx#setTimer} method passing in the delay and a handler
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example15}
 * ----
 *
 * The return value is a unique timer id which can later be used to cancel the timer. The handler is also passed the timer id.
 *
 * ==== Periodic Timers
 *
 * You can also set a timer to fire periodically by using {@link io.vertx.core.Vertx#setPeriodic}.
 *
 * There will be an initial delay equal to the period.
 *
 * The return value of `setPeriodic` is a unique timer id (long). This can be later used if the timer needs to be cancelled.
 *
 * The argument passed into the timer event handler is also the unique timer id:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example16}
 * ----
 *
 * ==== Cancelling timers
 *
 * To cancel a periodic timer, call {@link io.vertx.core.Vertx#cancelTimer} specifying the timer id. For example:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example17}
 * ----
 *
 * ==== Automatic clean-up in verticles
 *
 * If you're creating timers from inside verticles, those timers will be automatically closed
 * when the verticle is undeployed.
 *
 * [[event_bus]]
 * include::eventbus.adoc[]
 *
 * include::override/json.adoc[]
 *
 * include::buffers.adoc[]
 *
 * include::net.adoc[]
 *
 * include::http.adoc[]
 *
 * include::shareddata.adoc[]
 *
 * include::filesystem.adoc[]
 *
 * include::datagrams.adoc[]
 *
 * include::dns.adoc[]
 *
 * [[streams]]
 * include::streams.adoc[]
 *
 * include::parsetools.adoc[]
 *
 * == Thread safety
 *
 * Notes on thread safety of Vert.x objects
 *
 * == Metrics SPI
 *
 * By default Vert.x does not record any metrics. Instead it provides an SPI for others to implement which can be added
 * to the classpath. The metrics SPI is an advanced feature which allows implementers to capture events from Vert.x in
 * order to gather metrics. For more information on this, please consult the
 * {@link io.vertx.core.spi.metrics.VertxMetrics API Documentation}.
 *
 * == Clustering
 *
 * === Trouble-shooting clustering
 *
 * === High Availability
 *
 * == Security notes
 *
 * Warn about file uploads and serving files from arbitrary locations
 *
 * Vert.x is a tool kit
 *
 * Run in a security sandbox
 *
 * Use Apex
 *
 *
 */
@Document(fileName = "index.adoc")
@io.vertx.codegen.annotations.GenModule(name = "vertx")
package io.vertx.core;

import io.vertx.docgen.Document;
