require "vertx"
include Vertx

@server = HttpServer.new.request_handler do |req|
  req.response.end(java.lang.System.identityHashCode(self))
end.listen(8080)

def vertx_stop
  @server.close
end