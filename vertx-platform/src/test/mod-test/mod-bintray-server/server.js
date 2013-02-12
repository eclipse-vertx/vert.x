load('vertx.js')

vertx.createHttpServer().requestHandler(function(req) {
  console.log("uri is " + req.uri)
  if (req.uri.indexOf("..") !== -1) {
    req.response.statusCode = 403
    req.response.end()
  } else {
    var file = '.' + req.uri
    console.log("file is " + file)
    req.response.sendFile(file)
  }
}).listen(9192)
console.log("maven server started!!!")