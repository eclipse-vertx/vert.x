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

require "amqp"
include Amqp

queue = "test-queue"

# Create a channel, declare a queue, send some messages and consume them

AmqpClient.create_client.connect { |conn|
  conn.create_channel { |chan|
    chan.declare_queue(queue, false, true, true) {
      chan.subscribe(queue, true) { |props, body|
        puts "Received message #{body}"
      }
      (1..10).each { |i| chan.publish("", queue, "message #{i}") }
    }
  }
}

STDIN.gets
