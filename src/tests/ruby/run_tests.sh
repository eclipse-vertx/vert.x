#!/bin/sh

export CLASSPATH=../../../target/vert.x.jar:../../../lib/core/netty.jar:../../../lib/core/high-scale-lib.jar:../../../lib/opt/hazelcast-all-1.9.4.4.jar:../../../target/tests/classes
jruby -I../../../src/main/ruby run_tests.rb