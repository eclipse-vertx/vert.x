load('vertx.js')

var log = vertx.getLogger();

log.info("running http server")

var server = new vertx.HttpServer();

server.requestHandler(function(req) {
  req.response.end("hello world");
})

server.listen(8181, 'localhost');

log.info("server listening");

function vertxStop() {
  server.close
}
