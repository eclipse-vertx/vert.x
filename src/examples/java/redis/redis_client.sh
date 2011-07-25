#!/bin/sh

CLASSPATH=../target/examples/classes:../../../../target/node.x.jar:../../../main/resources/jars/netty.jar:../../../main/resources/jars/jedis/jedis-2.0.0.jar
java -classpath $CLASSPATH org.nodex.examples.redis.ClientExample

