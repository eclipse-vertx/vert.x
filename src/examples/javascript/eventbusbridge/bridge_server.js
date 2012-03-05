load('vertx.js')

var server = new vertx.HttpServer()
// Let everything through
new vertx.SockJSBridge(server, {prefix: "/eventbus"}, [{}]);

// Also serve the index page
server.requestHandler(function(req) {
  if (req.uri == "/") req.response.sendFile("eventbusbridge/index.html")
  if (req.uri == "/vertxbus.js") req.response.sendFile("eventbusbridge/vertxbus.js")
}).listen(8080)

function vertxStop() {
  server.close()
}