load('vertx.js')

var sent302 = false;
vertx.createHttpServer().requestHandler(function(req) {
  console.log("bintray server got uri: " + req.uri);
  if (req.uri.indexOf("..") !== -1) {
    req.response.statusCode = 403;
    req.response.end();
  } else {
    // bintray redirects to a CDN - so our client must understand redirects
    if (!sent302) {
      console.log("sent a 302");
      req.response.statusCode = 302;
      req.response.headers()['location'] = 'http://localhost:9192' + req.uri;
      req.response.end();
      sent302 = true;
    } else {
      console.log("serving file");
      var file = '.' + req.uri
      req.response.sendFile(file)
    }
  }
}).listen(9192)