#!/bin/sh

export CLASSPATH=../../../target/node.x.jar:../../main/resources/jars/netty.jar:../../main/resources/jars/high-scale-lib.jar:../../../target/tests/classes
jruby -I../../../src/main/ruby run_tests.rb