
load('vertx.js');

// Inspired from Sinatra / Express
var rm = new vertx.RouteMatcher();

// Extract the params from the uri
rm.get('/details/:user/:id', function(req) {
  req.response.end("User: " + req.params()['user'] + " ID: " + req.params['id'])
});

// Catch all - serve the index page
rm.get('.*', function(req) {
  req.response.sendFile("route_match/index.html");
});

var server = new vertx.HttpServer().requestHandler(rm).listen(8080);

function vertxStop() {
  server.close();
}