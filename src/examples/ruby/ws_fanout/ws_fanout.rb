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

require "vertx"
include Vertx

Vertx::go do
  conns = SharedData::get_set("conns")
  HttpServer.new.websocket_handler do |ws|
    conns.add(ws.text_handler_id)
    ws.data_handler do |data|
      conns.each { |handler_id| Vertx::send_to_handler(handler_id, data.to_s) }
    end
    ws.closed_handler { conns.delete(ws.write_handler_id) }
  end.request_handler do |req|
    req.response.send_file("ws_fanout/ws.html") if req.uri == "/"
  end.listen(8080)
end

puts "hit enter to exit"
STDIN.gets