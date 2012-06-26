load('vertx.js')

vertx.createHttpServer().requestHandler(function(req) {
  stdout.println("Got request " + req.uri);

  var hdrs = req.headers();
  for (k in hdrs) {
    stdout.println(k + ": " + hdrs[k])
  }

  req.dataHandler(function(data) { stdout.println("Got data " + data) });

  req.endHandler(function() {
    // Now send back a response
    req.response.setChunked(true)

    for (var i = 0; i < 10; i++) {
      req.response.write("server-data-chunk-" + i);
    }

    req.response.end();
  });
}).listen(8282)
