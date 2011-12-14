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

require 'test/unit'
require 'vertx'
require 'utils'
include Vertx

class SockJSTest < Test::Unit::TestCase

  def test_sockjs

    latch = Utils::Latch.new(1)

    Vertx::internal_go {
      server = HttpServer.new

      sjs_server = SockJSServer.new(server)

      prefix = "/myapp"

      config = {"prefix" => prefix}

      sjs_server.install_app(config) { |sock|
        sock.data_handler{ |buff| sock.write_buffer(buff)}
      }

      server.listen(8080)

      client = HttpClient.new
      client.port = 8080;

      client.connect_web_socket(prefix + "/server-id/session-id/websocket") { |ws|

        ws.data_handler do |buff|
          client.close
          server.close { latch.countdown}
        end

        ws.write_text_frame("foo")
      }
    }

    assert(latch.await(5))

  end
end