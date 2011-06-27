#!/bin/sh

export CLASSPATH=../../../target/node.x.jar:../../core/resources/jars/netty-3.2.4.Final.jar
export LOAD_PATH=../../api/ruby
jruby fanout_example.rb
