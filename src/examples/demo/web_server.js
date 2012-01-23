load('vertx.js')

var server = new vertx.HttpServer();

// Link up the client side to the server side event bus
var sockJSServer = new vertx.SockJSServer(server);
sockJSServer.installApp({prefix : '/eventbus'}, new vertx.SockJSBridgeHandler());

// Also serve the static resources
var routeMatcher = new vertx.RouteMatcher();
server.requestHandler(routeMatcher).listen(8080, 'localhost');

// TODO security on paths
routeMatcher.get(':path', function(req) {
  req.response.sendFile('web/' + req.getParameter('path'));
});

function vertxStop() {
  server.close();
}

