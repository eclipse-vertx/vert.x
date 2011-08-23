#!/bin/sh

CLASSPATH=../target/examples/classes:../../../../target/node.x.jar:../../../main/resources/jars/netty.jar
java -classpath $CLASSPATH org.nodex.examples.proxy.HttpClient

