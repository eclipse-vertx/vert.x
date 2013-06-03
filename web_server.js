var vertx = require('vertx')

var server = vertx.createHttpServer();

// Also serve the static resources
server.requestHandler(function(req) {
  if (req.path() == '/') {
    req.response.sendFile('index.html');
  } else if (req.path().indexOf('..') == -1) {
    req.response.sendFile("." + req.path());
  }
}).listen(8181, 'localhost');

