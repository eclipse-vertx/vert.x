load('vertx.js')

vertx.createHttpServer().requestHandler(function(req) {
  var filename = "sendfile/" + (req.uri == "/" ? "index.html" : "." + req.uri);
  req.response.sendFile(filename)
}).listen(8080)