#!/bin/sh

CLASSPATH=./target/examples/classes:../../../target/node.x.jar:../../core/resources/jars/netty-3.2.4.Final.jar
java -classpath $CLASSPATH org.nodex.examples.net.EchoClient

