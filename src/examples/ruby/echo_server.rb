#!/usr/bin/env CLASSPATH=../../../target/node.x.jar:../../core/resources/jars/netty-3.2.4.Final.jar jruby echo_server.rb

require "../../api/ruby/net"

server = Net.create_server{|socket| socket.data{|data| socket.write(data)}}.listen(8080, "127.0.0.1")

#Prevent script from exiting
STDIN.gets                      



