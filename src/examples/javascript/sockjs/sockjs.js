load('vertx.js')

var server = vertx.createHttpServer()

// Serve the index page
server.requestHandler(function(req) {
  if (req.uri == "/") req.response.sendFile("sockjs/index.html")
});

var sjsServer = new vertx.createSockJSServer(server)

// The handler for the SockJS app - we just echo data back
sjsServer.installApp({prefix: "/testapp"}, function(sock) {
  sock.dataHandler(function(buff) {
    sock.writeBuffer(buff)
  })
});

server.listen(8080)