#!/bin/sh

CLASSPATH=../../../target/node.x.jar:../../core/resources/jars/netty-3.2.4.Final.jar
LOAD_PATH=$LOAD_PATH:/home/tfox/projects/node.x/src/api/ruby
jruby echo_server.rb
