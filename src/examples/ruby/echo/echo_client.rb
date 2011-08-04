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
require "buffer"
include Net

# A simple echo client which sends some dataHandler and displays it as it gets echoed back
# Make sure you start the echo server before running this

Client.create_client.connect(8080, "localhost") { |socket|
  socket.data { |data| puts "Echo client received #{data.to_s}" }
  (1..10).each { |i|
    str = "hello #{i}\n"
    puts "Echo client sending #{str}"
    socket.write(Buffer.from_str(str))
  }
}

puts "hit enter to stop"
STDIN.gets


