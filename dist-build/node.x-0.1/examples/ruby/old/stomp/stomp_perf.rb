# Copyright 2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require "stomp"
require "buffer"
include Stomp

warmup = 500000;
num_messages = 1000000;
count = 0
start = nil

Client.connect(8181, "localhost") { |conn|
  conn.subscribe("test-topic") { |msg|
    count += 1
    if count == warmup + num_messages
      puts "elapsed time is #{Time.now - start}"
      rate = (num_messages) / (Time.now - start)
      puts "Rate is #{rate}"
    end
  }
  buf = Buffer.from_str("msg")
  (1..warmup).each { |i|
    conn.send("test-topic", buf)
  }
  start = Time.now
  (1..num_messages).each { |i|
    conn.send("test-topic", buf)
  }
}

STDIN.gets

