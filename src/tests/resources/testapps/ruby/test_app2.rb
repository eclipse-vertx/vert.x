require "vertx"
include Vertx

if $instance_count = nil
  $instance_count = java.util.concurrent.atomic.AtomicLong.new(0)
end

$instance_count.incrementAndGet

@server = HttpServer.new.request_handler do |req|
  req.response.end($instance_count.get)
end.listen(8080)

def vertx_stop
  @server.close
end