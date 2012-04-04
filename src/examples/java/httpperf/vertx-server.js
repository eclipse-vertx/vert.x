load('vertx.js')

vertx.createHttpServer().requestHandler(function(req) {
  req.response.end();
}).listen(8080, 'localhost');

