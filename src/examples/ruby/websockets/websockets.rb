# Copyright 2011 VMware, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require "nodex"
include Nodex

Nodex::go {
  HttpServer.new.websocket_handler { |ws|
    ws.close if ws.uri != "/myapp"
    ws.data_handler { |buffer|
      ws.write_text_frame(buffer.to_s)
    }
  }.request_handler { |req|
    req.response.send_file("websockets/ws.html") if req.uri == "/"
  }.listen(8080)
}

puts "hit enter to exit"
STDIN.gets