load('vertx.js')

var server = new vertx.HttpServer()

// Serve the index page
server.requestHandler(function(req) {
  if (req.uri == "/") req.response.sendFile("sockjs/index.html")
});

var sjsServer = new vertx.SockJSServer(server)

// The handler for the SockJS app - we just echo data back
sjsServer.installApp({prefix: "/testapp"}, function(sock) {
  sock.dataHandler(function(buff) {
    sock.writeBuffer(buff)
  })
});

server.listen(8080)

function vertxStop() {
  server.close()
}