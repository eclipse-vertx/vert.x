#!/bin/sh

export CLASSPATH=../../../target/node.x.jar:../../main/resources/jars/netty-3.2.4.Final.jar:../../main/resources/jars/jedis/jedis-2.0.0.jar
jruby -I../../main/ruby redis_client.rb
