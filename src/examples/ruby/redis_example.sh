#!/bin/sh

export CLASSPATH=../../../target/node.x.jar:../../main/resources/jars/netty-3.2.4.Final.jar
jruby -I../../main/ruby redis_example.rb
