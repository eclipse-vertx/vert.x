#!/bin/sh

CLASSPATH=../target/examples/classes:../../../../target/node.x.jar:../../../main/resources/jars/netty.jar:../../../main/resources/jars/rabbit/rabbitmq-client.jar:../../../main/resources/jars/rabbit/commons-io-1.2.jar:../../resources/jars/rabbit/commons-cli-1.1.jar
java -classpath $CLASSPATH org.nodex.java.examples.old.amqp.ClientExample

