require "vertx"
include Vertx

HttpServer.new.request_handler do |req|
  req.response.end
end.listen(8080)
