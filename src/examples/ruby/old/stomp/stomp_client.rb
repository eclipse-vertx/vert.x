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

require "stomp"
require "buffer"
include Stomp

topic = "test-topic"

# A very simple example using STOMP to send and consume some messages
# Use this with the java STOMP server in src/examples/java/stomp

StompClient.connect(8181) do |conn|
  conn.subscribe(topic) { |headers, body| puts "Got message #{body}" }
  (1..10).each { |i| conn.send(topic, Buffer.from_str("hello #{i}\n")) }
end

STDIN.gets

