# Node.x

## What is Node.x?

* A general purpose asynchronous framework that uses an event based style for building highly scalable applications
* Runs on the JVM.
* Everything is non-blocking.
* Embraces the style of node.js and extends it to the JVM. Think node.js *on steroids*.
* Polyglot. The same (or similar) API will be available in multiple languages: Initially Ruby (JRuby),
Groovy and Java and going ahead... JavaScript, Python (Jython), Clojure, Scala etc.
* Goes with the recent developments with InvokeDynamic in Java 7 and bets on the JVM being the future premier runtime for dynamic languages.
* Enables you to create network servers or clients incredibly easily.
* True threading. Unlike node.js, Python Twisted or Ruby EventMachine, it has true multi-threaded scalability. No more spinning up 32 instances just to utilise the cores on your server.
* Incredibly simple concurrency model. Write your code as single threaded like node.js, watch it scale across multiple cores (unlike node.js)
* Understands multiple network protocols out of the box including: TCP, SSL, UDP, HTTP, HTTPS, Websockets
* Sendfile support for writing super scalable web servers
* Will provide plugins for talking AMQP, STOMP, Redis etc
* Provides an elegant api for composing asynchronous actions together. Glue together HTTP, AMQP, Redis or whatever in a few lines of code.

## Jump to the examples

Take a look at some of these working Ruby examples to see the kind of things you can do with Node.x

[Ruby examples](https://github.com/purplefox/node.x/tree/master/src/examples/ruby "Ruby examples")

[Java examples](https://github.com/purplefox/node.x/tree/master/src/examples/java "Java examples")

## What is the status of Node.x?

We have finished the proof of concept, and work is now in progress for the 1.0 alpha release in a few months time

## What is the architecture?

Node.x *core* is written in Java. We then provide a thin layer in each of the JVM languages we support which allows the API to be used in each of the supported languages.

We do not expose the Java API directly in the other languages since we wish to retain the normal coding idioms for each supported language.

We don't leak Java stuff to other languages through the API.

Node.x internally uses [Netty](https://github.com/netty/netty "Netty") for much of the asynchronous IO.

## Building

### Pre-requisites

* Operating system: Node.x currently runs on Linux and OSX. If you're on Windows, create a Linux virtual machine using your favourite virtualisation software and use that.
* Apache ant - This is the build tool currently used. Make sure the ant bin directory is on your PATH.
* JDK, version 1.7.0 or later. You can use OpenJDK or the official Oracle JDK. Make sure the JDK bin directory is on your PATH.
* JRuby, version 1.6.4 or later. Make sure the JRuby bin directory is on your PATH
* Yard, for building the Ruby API documentation. To install yard 'jruby -S gem install rdiscount' followed by 'jruby -S gem install yard'  
* Groovy. Make sure the Groovy bin directory is on your PATH.   

### To build core

From root directory 'ant'

### To build docs

* To build javadoc: 'ant javadoc'
* To build Ruby yard docs: 'ant yardoc'

### To run tests

* To run Java tests: 'ant javatests'
* To run Ruby tests: 'ant rubytests'
* To run all tests: 'ant tests'

### To build a distro

From root directory 'ant dist'

The distro tar.gz will be created in the 'target' directory

## Installation

First you will need to obtain a distro. Once we have done some releases you'll be able to obtain a pre-made distro and
install that. But, it's early days, so for now, you will have to build one yourself. See the previous section for how to do that.

### Pre-requisites

* Operating system: Node.x currently runs on Linux and OSX. If you're on Windows, create a Linux virtual machine using your favourite virtualisation software and use that.
* Everyone will need JDK, version 1.7.0 or later. You can use OpenJDK or the official Oracle JDK. Make sure the JDK bin directory is on your PATH.

#### Java API

If you're just using the Java API you just need the JDK installed. If you want to run the Java examples from the distro, you will need Apache ant installed too.

#### Ruby API

If you're using the Ruby API you will also need to install JRuby, version 1.6.4 or later. Make sure the JRuby bin directory is on your PATH

#### Groovy API

If you're using the Groovy API you will also need to install Groovy. Make sure the Groovy bin directory is on your PATH.

### To install the distro

Unzip the distro somewhere, e.g. in your home directory

Make sure the bin directory from the distro is on your PATH.

### To run node.x

#### Java

From anywhere 'nodex-java -cp my_classpath org.foo.MyMainClass'

Where org.foo.MyMainClass is a fully qualified class name of your main class.

Where my_classpath is a classpath which allows org.foo.MyMainClass to be located along with any other dependencies of your application

The nodex-java basically takes the same params as the 'java' command

#### Ruby

From anywhere 'nodex-ruby my_ruby_script.rb'

Where my_ruby_script.rb is the script to execute.

nodex-ruby takes the same arguments as the jruby command, so you can pass in -I etc as necessary, if you want to add extra
stuff to the LOAD_PATH

#### Groovy

TODO

## Examples

### Java

Java examples are [here] (https://github.com/purplefox/node.x/tree/master/src/examples/java "Java examples")

Examples must be run from a distro. First install the distro, then cd to the examples/java directory.

Then read the README there.

### Ruby

Ruby examples are [here] (https://github.com/purplefox/node.x/tree/master/src/examples/ruby "Ruby examples")

Examples must be run from a distro. First install the distro, then cd to the examples/ruby directory.

Then read the README there.

## Development discussions

[node.x dev Google Group](http://groups.google.com/group/nodex-dev "Node.x dev")

## IRC

There's an IRC channel at irc.freenode.net#nodex if you want to drop in to chat about any user or development topics

## Join us!!

There is lots to do! Ping me twitter:@timfox, or drop a mail on the nodex-dev google group.
