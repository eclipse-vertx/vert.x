load('vertx.js')


vertx.createHttpServer().requestHandler(function(req) {
  console.log("got uri " + req.uri);
  if (req.uri.indexOf("..") !== -1) {
    req.response.statusCode = 403
    req.response.end()
  } else {
    var file = '.' + req.uri
    req.response.sendFile(file)
  }
}).listen(9192)

console.log("Maven server started! on localhost: " + 9192)