# Node.x

## What is Node.x?

* A general purpose framework that uses an asynchronous event based style for building highly scalable network or file system aware applications
* Runs on the JVM.
* Embraces the style of node.js and extends it to the JVM. Think node.js *on steroids* plus some.
* Polyglot. The same (or similar) API will be available in multiple languages: Ruby, Java, Groovy, Python etc
* Embraces the recent developments with invoke_dynamic in Java 7 and bets on the JVM being the future premier runtime for dynamic languages.
* Enables you to create network servers or clients incredibly easily.
* True threading. Unlike node.js, python twisted or Ruby Eventmachine, it has true multi-threaded scalability. No more spinning up 32 instances just to utilise the cores on your server.
* Understands multiple protocols out of the box including: TCP, SSL, UDP, HTTP, HTTPS, Websockets, AMQP, STOMP, Redis etc
* Provides an elegant api for composing asynchronous actions together. Glue together HTTP, AMQP, Redis or whatever in a few lines of code.

## What is the status of Node.x?

It's at the proof of concept stage. I have implemented enough to create a few working examples but the real work is yet to come!

Take a look at some of these working Ruby examples to see the kind of things you can do with Node.x

[Ruby examples](src/examples/ruby "Ruby examples")

## What is the architecture?

Node.x *core* is written in Java. We then provide a thin layer in each of the JVM languages we support which allows the API to be used in each of the supported languages.

We do not expose the Java API directly in the other languages since we wish to retain the normal coding idioms for each supported language. (E.g. A Ruby hash has a different API from a Java map - from Ruby the hash API should be used).

Node.x internally uses [Netty](http://www.jboss.org/netty "Netty") for much of the asynchronous IO.

## Building

Pre-requisites: ant, jruby, java

### To build core

From root directory 'ant'

### Examples

Ruby examples are in 'src/examples/ruby'

To run example, make sure core is built first, then cd to example directory, read the README (if it exists), then run the .sh file. (Sorry no Windows batch files as yet)

## Join us!!




