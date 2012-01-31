load('vertx.js')

var server = new vertx.HttpServer();

// Also serve the static resources
server.requestHandler(function(req) {
  if (req.path == '/') {
    req.response.sendFile('web/index.html');
  } else if (req.path.indexOf('..') == -1) {
    req.response.sendFile('web' + req.path);
  }
}).listen(8080, 'localhost');

function vertxStop() {
  server.close();
}

