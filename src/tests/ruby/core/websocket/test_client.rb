# Copyright 2011-2012 the original author or authors.
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
require "test_utils"

@tu = TestUtils.new
@tu.check_context
@server = HttpServer.new
@client = HttpClient.new
@client.port = 8080

def test_echo_binary
  echo(true)
end

def test_echo_text
  echo(false)
end

def echo(binary)

  @server.websocket_handler do |ws|

    @tu.check_context

    ws.data_handler do |buff|
      @tu.check_context
      ws.write_buffer(buff)
    end

  end

  @server.listen(8080)

  if binary
    buff = TestUtils.gen_buffer(1000)
  else
    str = TestUtils.random_unicode_string(1000)
    buff = Buffer.create(str)
  end

  @client.connect_web_socket("/someurl") do |ws|
    @tu.check_context

    received = Buffer.create()

    ws.data_handler do |buff|
      @tu.check_context
      received.append_buffer(buff)
      if received.length == buff.length
        @tu.azzert(TestUtils.buffers_equal(buff, received))
        @tu.test_complete
      end
    end

    if binary
      ws.write_binary_frame(buff)
    else
      ws.write_text_frame(str)
    end
  end

end

def test_write_from_connect_handler

  @server.websocket_handler do |ws|
    @tu.check_context
    ws.write_text_frame("foo")
  end

  @server.listen(8080)

  @client.connect_web_socket("/someurl") do |ws|
    @tu.check_context
    ws.data_handler do |buff|
      @tu.check_context
      @tu.azzert("foo" == buff.to_s)
      @tu.test_complete
    end
  end

end

def test_close

  @server.websocket_handler do |ws|
    @tu.check_context
    ws.data_handler do |buff|
      ws.close
    end
  end

  @server.listen(8080)

  @client.connect_web_socket("/someurl") do |ws|
    @tu.check_context
    ws.closed_handler do
      @tu.test_complete
    end
    ws.write_text_frame("foo");
  end

end

def test_close_from_connect

  @server.websocket_handler do |ws|
    @tu.check_context
    ws.close
  end

  @server.listen(8080)

  @client.connect_web_socket("/someurl") do |ws|
    @tu.check_context
    ws.closed_handler do
      @tu.test_complete
    end
  end

end

def vertx_stop
  @tu.check_context
  @tu.unregister_all
  @client.close
  @server.close do
    @tu.app_stopped
  end
end

@tu.register_all(self)
@tu.app_ready
