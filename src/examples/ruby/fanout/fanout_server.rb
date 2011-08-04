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
include Net

# This server fans out any dataHandler it receives to all connected clients

Server.create_server { |socket|
  (@sockets ||= []) << socket
  socket.data {
      |data| @sockets.each {
        |socket| socket.write(data)
    }
  }
}.listen(8080)

puts "hit enter to stop server"
STDIN.gets
server.stop

