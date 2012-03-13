load('vertx.js')

var server = new vertx.HttpServer()

// Serve the static resources
server.requestHandler(function(req) {
  if (req.uri == "/") req.response.sendFile("eventbusbridge/index.html")
  if (req.uri == "/vertxbus.js") req.response.sendFile("eventbusbridge/vertxbus.js")
})

// Create a SockJS bridge which lets everything through (be careful!)
new vertx.SockJSBridge(server, {prefix: "/eventbus"}, [{}]);

server.listen(8080)

function vertxStop() {
  server.close()
}