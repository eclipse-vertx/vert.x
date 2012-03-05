load('vertx.js')

var server = new vertx.HttpServer().websocketHandler(function(ws) {
  var pump = new vertx.Pump(ws, ws);
  pump.start();
}).requestHandler(function(req) {
  if (req.uri == "/") req.response.sendFile("websockets/ws.html")
}).listen(8080)

function vertxStop() {
  server.close()
}
