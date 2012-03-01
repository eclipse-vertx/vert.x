load('vertx.js')

var server = new vertx.HttpServer();

server.requestHandler(function(req) {
  req.response.end("<html><body><h1>Hello from vert.x!</h1></body></html>");
}).listen(8080, 'localhost');

function vertxStop() {
  server.close();
}
