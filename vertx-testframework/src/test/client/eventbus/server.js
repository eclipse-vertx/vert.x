load('vertx.js')

var server = vertx.createHttpServer();

server.requestHandler(function(req) {
  if (req.path == '/') {
    req.response.sendFile('index.html');
  } else if (req.path.indexOf('..') == -1) {
    req.response.sendFile("." + req.path);
  }
});

new vertx.SockJSBridge(server, {prefix : '/eventbus'},[{}]);

server.listen(8181, 'localhost');

function vertxStop() {
  server.close();
}
