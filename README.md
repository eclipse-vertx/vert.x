# Node.x

## What is Node.x?

* A general purpose framework that uses an asynchronous event based style for building highly scalable applications
* Runs on the JVM.
* Everything is asynchronous.
* Embraces the style of node.js and extends it to the JVM. Think node.js *on steroids*. Plus some.
* Polyglot. The same (or similar) API will be available in multiple languages: Ruby, Java, Groovy, (Python, JS, Clojure, Scala)
* Goes with the recent developments with InvokeDynamic in Java 7 and bets on the JVM being the future premier runtime for dynamic languages.
* Enables you to create network servers or clients incredibly easily.
* True threading. Unlike node.js, Python Twisted or Ruby EventMachine, it has true multi-threaded scalability. No more spinning up 32 instances just to utilise the cores on your server.
* Incredibly simple concurrency model. Write your code as single threaded like node.js, watch it scale across multiple cores (unlike node.js)
* Understands multiple network protocols out of the box including: TCP, SSL, UDP, HTTP, HTTPS, Websockets
* Sendfile support for writing super scalable web servers
* Plugins for talking AMQP, STOMP, Redis etc
* Provides an elegant api for composing asynchronous actions together. Glue together HTTP, AMQP, Redis or whatever in a few lines of code.

## Ok, cut the crap, show me some examples

Take a look at some of these working Ruby examples to see the kind of things you can do with Node.x

[Ruby examples](https://github.com/purplefox/node.x/tree/master/src/examples/ruby "Ruby examples")

## What is the status of Node.x?

We have finished the proof of concept, and work is now in progress for the 1.0 alpha release in a few months time

## What is the architecture?

Node.x *core* is written in Java. We then provide a thin layer in each of the JVM languages we support which allows the API to be used in each of the supported languages.

We do not expose the Java API directly in the other languages since we wish to retain the normal coding idioms for each supported language.

We don't leak Java stuff to other languages through the API.

Node.x internally uses [Netty](http://www.jboss.org/netty "Netty") for much of the asynchronous IO.

## Building

Pre-requisites: ant, jruby, java 7

Node.x is java 7+ only. We use the new async file IO, and extended file system API in Java 7.

Also JRuby runs better with Java 7 due to InvokeDynamic support

### To build core

From root directory 'ant'

## Examples

Ruby examples are [here] (https://github.com/purplefox/node.x/tree/master/src/examples/ruby "Ruby examples")

To run example, make sure core is built first, then cd to example directory, read the README (if it exists), then run the .sh file. (Sorry no Windows batch files as yet)

## Development discussions

[node.x dev Google Group](http://groups.google.com/group/nodex-dev "Node.x dev")

## Join us!!

There is lots to do! Ping me @timfox, or drop a mail on the nodex-dev google group.




