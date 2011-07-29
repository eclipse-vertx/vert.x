# Copyright 2002-2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use
# this file except in compliance with the License. You may obtain a copy of the
# License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

require "net"
require "parsetools"
include Net

# A trivial publish-subscribe server.
# The server understands the following commands:
# SUBSCRIBE,<topic name> - to subscribe to a named topic
# UNSUBSCRIBE,<topic name> - to unsubscribe to the named topic
# PUBLISH,<topic name>,<string to publish> - all subscribers to the named topic will get the published string
#
# To try this out open a few sessions using telnet localhost 8080 and type the protocol by hand

Server.create_server { |socket|
  parser = ParserTools::RecordParser.new_delimited("\n") { |line|
    line = line.to_s.rstrip
    if line.start_with?("subscribe,")
      topic_name = line.split(",", 2)[1]
      puts "subscribing to #{topic_name}"
      @topics ||= {}
      topic = @topics[topic_name]
      if (topic.nil?)
        topic = []
        @topics[topic_name] = topic
      end
      topic << socket
    elsif line.start_with?("publish,")
      sp = line.split(',', 3)
      puts "publishing to #{sp[1]} with #{sp[2]}"
      topic = @topics[sp[1]]
      if (topic)
        topic.each { |socket| socket.write(Buffer.from_str(sp[2])) }
      end
    end
  }
  socket.data(parser)
}.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop
