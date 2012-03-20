load('vertx.js')

new vertx.HttpServer().requestHandler(function(req) {
  req.response.end();
}).listen(8080, 'localhost');

