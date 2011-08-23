#!/bin/sh

CLASSPATH=../target/examples/classes:../../../../target/node.x.jar:../../../main/resources/jars/netty.jar:../../../main/resources/jars/high-scale-lib.jar
java -classpath $CLASSPATH org.nodex.examples.echo.EchoServer

