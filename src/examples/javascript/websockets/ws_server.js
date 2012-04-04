load('vertx.js')

vertx.createHttpServer().websocketHandler(function(ws) {
  ws.dataHandler( function(buffer) { ws.writeTextFrame(buffer.toString()) });
}).requestHandler(function(req) {
  if (req.uri == "/") req.response.sendFile("websockets/ws.html")
}).listen(8080)

