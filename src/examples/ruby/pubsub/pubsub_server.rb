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

require "core/net"
require "core/nodex"
require "core/shared_data"
require "core/parsetools"
require "core/buffer"

include Net

Nodex::go {
  Server.new.connect_handler { |socket|
    parser = ParseTools::RecordParser.new_delimited("\n") { |line|
      line = line.to_s.rstrip
      if line.start_with?("subscribe,")
        topic_name = line.split(",", 2)[1]
        puts "subscribing to #{topic_name}"
        topic = SharedData::get_set(topic_name)
        topic.add(socket.write_handler_id)
      elsif line.start_with?("unsubscribe,")
        topic_name = line.split(",", 2)[1]
        puts "unsubscribing from #{topic_name}"
        topic = SharedData::get_set(topic_name)
        topic.delete(socket.write_handler_id)
        SharedData::remove_set(topic_name) if topic.empty?
      elsif line.start_with?("publish,")
        sp = line.split(',', 3)
        puts "publishing to #{sp[1]} with #{sp[2]}"
        topic = SharedData::get_set(sp[1])
        puts "topic is #{topic}"
        topic.each { |actor_id| Nodex::send_to_handler(actor_id, Buffer.create_from_str(sp[2])) }
      end
    }
    socket.data_handler(parser)
  }.listen(8080)
}

puts "hit enter to exit"
STDIN.gets
server.stop
