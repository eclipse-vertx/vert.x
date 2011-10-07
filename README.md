# Node.x

## What is Node.x?

The next generation polyglot asynchronous application container.

* Runs on the JVM.
* Everything is non-blocking.
* Embraces the good bits of event driven frameworks like node.js without the bad bits. Then adds some even juicier bits.
* Polyglot. Node.x will be usable from multiple languages: Ruby (JRuby), Groovy, Java, JavaScript, Python, Clojure, Scala. Write your code in the language *you* choose. Leverage the power of node.x from that language.
* Goes with the recent developments with InvokeDynamic in Java 7 and bets on the JVM being the future premier runtime for dynamic languages.
* Enables you to create network servers or clients incredibly easily.
* True scalability. Unlike other well-known event-driven frameworks, you can have more than one event loop per process. No more spinning up 32 instances just to utilise the cores on your server.
* Incredibly simple concurrency model. Write your code as single threaded but watch it scale across multiple cores. No need to worry about concurrency.
* Understands multiple network protocols out of the box including: TCP, SSL, UDP, HTTP, HTTPS, Websockets
* Efficiently serve static files from the filesystem bypassing user-space altogether.
* Provides an elegant api for composing asynchronous actions together. Glue together HTTP, AMQP, Redis or whatever in a few lines of code.
* Asynchronous plugins will be provided for a wide variety of other systems and protocols including AMQP, Redis, REST, Gemfire, MongoDB, STOMP, Twitter, SMTP, JDBC, Memcached, JMS, ZeroMQ, JDBC, web frameworks, etc
* Module manager. Install modules from a public repository. Create your own applications and push them to the repo. Creates a market for an ecosystem of node.x modules.

## Jump to the examples

Take a look at some of these working Ruby examples to see the kind of things you can do with Node.x

[Ruby examples](https://github.com/purplefox/node.x/tree/master/src/examples/ruby "Ruby examples")

[Java examples](https://github.com/purplefox/node.x/tree/master/src/examples/java "Java examples")

## What is the status of Node.x?

We are working on getting the first binary release out ASAP.

## What is the architecture?

Node.x *core* is written in Java. We then provide a thin layer in each of the JVM languages we support which allows the API to be used in each of the supported languages.

We do not expose the Java API directly in the other languages since we wish to retain the normal coding idioms for each supported language.

We don't leak Java stuff to other languages through the API.

Node.x internally uses [Netty](https://github.com/netty/netty "Netty") for much of the asynchronous IO.

## Binary downloads

Binary releases are available here

## Building from source

Instructions for building node.x from source are [here](https://github.com/purplefox/node.x/wiki/Build-instructions)

## Installation and running the examples

Instructions for installing and running node.x are are [here] (https://github.com/purplefox/node.x/wiki/Installation-instructions)

## Development discussions

[node.x dev Google Group](http://groups.google.com/group/nodex-dev "Node.x dev")

## IRC

There's an IRC channel at irc.freenode.net#nodex if you want to drop in to chat about any user or development topics

## Join us!!

There is lots to do! We are looking for contributors - both individual and corporate. Ping me twitter:@timfox, or drop a mail on the nodex-dev google group.
