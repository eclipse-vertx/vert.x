#!/bin/sh

export CLASSPATH=../../../../target/node.x.jar:../../../main/resources/jars/netty.jar
jruby -I../../../main/ruby proxy_server.rb
