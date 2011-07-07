#!/bin/sh

CLASSPATH=../target/examples/classes:../../../../target/node.x.jar:../../../main/resources/jars/netty-3.2.4.Final.jar:../../../main/resources/jars/rabbit/rabbitmq-client.jar:../../../main/resources/jars/rabbit/commons-io-1.2.jar:../../resources/jars/rabbit/commons-cli-1.1.jar
java -classpath $CLASSPATH org.nodex.examples.amqp.ClientExample

