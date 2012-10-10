load('vertx.js')

var server = vertx.createHttpServer()
var sockjsServer;

var log = [];

// Serve the static resources
server.requestHandler(function(req) {
  if (req.uri == "/") req.response.sendFile("eventbusbridgelistener/index.html")
  if (req.uri == "/log.json") req.response.end(JSON.stringify(log));
  if (req.uri == "/vertxbus.js") req.response.sendFile("eventbusbridgelistener/vertxbus.js")
});


var myListener = {
  clientDisconnected: function(clientId) {
    log.push(clientId + ' disconnected');
  }
};

// Create a SockJS bridge which lets everything through (be careful!)
vertx.createSockJSServer(server).setEventBusBridgeListener(myListener).bridge({prefix: "/eventbus"}, [{}], [{}]);

server.listen(8080);