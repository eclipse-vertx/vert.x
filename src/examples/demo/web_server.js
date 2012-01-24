load('vertx.js')

var server = new vertx.HttpServer();

// Link up the client side to the server side event bus
var sockJSServer = new vertx.SockJSServer(server);
var handler = new vertx.SockJSBridgeHandler();
handler.addMatches(
  // Let through orders posted to the order queue
  {
    address : 'demo.orderQueue',
    match : {
    }
  },
  // Allow calls to get static album data from the persistor
  {
    address : 'demo.persistor',
    match : {
      action : 'find',
      collection : 'albums'
    }
  }
);
sockJSServer.installApp({prefix : '/eventbus'}, handler);

// Also serve the static resources
server.requestHandler(function(req) {
  if (req.path == '/') {
    req.response.sendFile('web/index.html');
  } else if (req.path.indexOf('..') == -1) {
    req.response.sendFile('web' + req.path);
  }
}).listen(8080, 'localhost');

function vertxStop() {
  server.close();
}

