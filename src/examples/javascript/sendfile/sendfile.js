load('vertx.js')

new vertx.HttpServer().requestHandler(function(req) {
  var filename = "sendfile/" + (req.uri == "/" ? "index.html" : "." + req.uri);
  req.response.sendFile(filename)
}).listen(8080)