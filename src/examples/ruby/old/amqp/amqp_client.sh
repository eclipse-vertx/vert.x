#!/bin/sh

export CLASSPATH=../../../../target/vert.x.jar:../../../main/resources/jars/netty-3.2.4.Final.jar:../../../main/resources/jars/rabbit/rabbitmq-client.jar:../../../main/resources/jars/rabbit/commons-io-1.2.jar:../../resources/jars/rabbit/commons-cli-1.1.jar
jruby -I../../../main/ruby amqp_client.rb
