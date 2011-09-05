#!/bin/sh

CLASSPATH=../../../../target/node.x.jar:../../../main/resources/jars/netty.jar:../../../main/resources/jars/high-scale-lib.jar:../../../main/groovy
groovy -classpath $CLASSPATH echo_server.groovy

