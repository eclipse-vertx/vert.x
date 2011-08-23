#!/bin/sh

export CLASSPATH=../../../../target/node.x.jar:../../..//main/resources/jars/netty.jar:../../../main/resources/jars/high-scale-lib.jar
jruby -I../../../main/ruby pubsub_server.rb
