#!/bin/sh

export CLASSPATH=../../../target/node.x.jar:../../main/resources/jars/netty.jar:../../../target/tests/classes
jruby -I../../../src/main/ruby run_tests.rb