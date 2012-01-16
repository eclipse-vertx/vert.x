require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new
@tu.check_context

def test_sock_js

  puts "in test sock js"

  server = HttpServer.new

  sjs_server = SockJSServer.new(server)

  prefix = "/myapp"

  config = {"prefix" => prefix}

  sjs_server.install_app(config) { |sock|
    @tu.check_context
    sock.data_handler{ |buff| sock.write_buffer(buff)}
  }

  server.listen(8080)

  client = HttpClient.new
  client.port = 8080;

  client.connect_web_socket(prefix + "/server-id/session-id/websocket") { |ws|
    @tu.check_context
    ws.data_handler do |buff|
      @tu.check_context
      client.close
      server.close {
        @tu.check_context
        @tu.test_complete
      }
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
