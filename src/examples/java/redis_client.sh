#!/bin/sh

CLASSPATH=./target/examples/classes:../../../target/node.x.jar:../../main/resources/jars/netty-3.2.4.Final.jar:../../main/resources/jars/jedis/jedis-2.0.0.jar
java -classpath $CLASSPATH org.nodex.examples.redis.RedisClient

