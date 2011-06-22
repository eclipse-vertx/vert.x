#!/bin/sh

CLASSPATH=../../../target/node.x.jar:../../core/resources/jars/netty-3.2.4.Final.jar
jruby echo_server.rb
