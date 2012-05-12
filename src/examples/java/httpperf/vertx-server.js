load('vertx.js')

vertx.createHttpServer().requestHandler(function(req) {
  //req.response.end();
  //req.response.sendFile("httpperf/foo.html");

  vertx.fileSystem.readFile("httpperf/foo.html", function(err, file) {
    req.response.headers["Content-Length"] = file.length()
    req.response.headers["Content-Type"] = "text/html"
    req.response.end(file)
  });
}).listen(8080, 'localhost');

