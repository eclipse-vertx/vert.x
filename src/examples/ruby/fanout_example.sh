#!/bin/sh

export CLASSPATH=../../../target/node.x.jar:../../core/resources/jars/netty-3.2.4.Final.jar
jruby -I../../api/ruby fanout_example.rb
