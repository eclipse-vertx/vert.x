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
  sendingMessage: function(clientId, address, message) {
    log.push(clientId + ' sent ' + message.encode() + ' to ' + address);
    return true; // let the client send the message
  },
  publishingMessage: function(clientId, address, message) {
    log.push(clientId + ' tried to publish ' + message.encode() + ' to ' + address);
    return false; // don't let any publish methods through
  },
  registeringHandler: function(clientId, address) {
    if (address === 'secret') {
      log.push('did not allow ' + clientId + ' registering at ' + address);
      return false;
    } else {
      log.push(clientId + ' registered at ' + address);
      return true;
    }
  },
  unregisteredHandler: function(clientId, address) {
    log.push(clientId + ' unregistered handler at ' + address);
  },
  clientDisconnected: function(clientId) {
    log.push(clientId + ' disconnected');
  }
};

// Create a SockJS bridge which lets everything through (be careful!)
vertx.createSockJSServer(server).setEventBusBridgeListener(myListener).bridge({prefix: "/eventbus"}, [{}], [{}]);

server.listen(8080);