require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new
@tu.check_context
@server = HttpServer.new
@client = HttpClient.new
@client.port = 8080

def test_websocket

  server = HttpServer.new

  server.websocket_handler { |param|
    @tu.check_context
    if param.is_a? String
      true
    else
      ws = param
      ws.data_handler{ |buff|
        @tu.check_context
        ws.write_buffer(buff)
      }
    end
  }
  server.listen(8080)

  client = HttpClient.new
  client.port = 8080;

  client.connect_web_socket("/someurl") { |ws|
    @tu.check_context

    ws.data_handler do |buff|
      @tu.check_context
      client.close
      server.close { @tu.test_complete }
    end

    ws.write_text_frame("foo")
  }

end

def vertx_stop
  @tu.check_context
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
