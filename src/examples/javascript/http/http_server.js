load('vertx.js')

log.println("running http server")

var server = new vertx.HttpServer();

server.requestHandler(function(req) {
  req.response.end("hello world");
})

server.listen(8181, 'localhost');

log.println("server listening");

function vertxStop() {
  server.close
}
