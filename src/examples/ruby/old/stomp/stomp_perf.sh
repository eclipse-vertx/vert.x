#!/bin/sh

export CLASSPATH=../../../../target/vert.x.jar:../../../main/resources/jars/netty-3.2.4.Final.jar
jruby -I../../../main/ruby stomp_perf.rb
