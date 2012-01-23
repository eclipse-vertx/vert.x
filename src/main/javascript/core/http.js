var vertx = vertx || {};

vertx.HttpServer = function() {
  /*
  There's a bug in Rhino which means we can't pass the handler function directly into the Java object
  connectHandler method, since, when closing the server, Rhino attempts to call equals() on it, but instead
  ends up calling the handler itself, then barfs.
  To workaround this we need to wrap the server and override the requestHandler and websocketHandler methods, then
  wrap the handlers temselves and pass them in.
  It's ugly but it works.
   */
//  var server = new JavaAdapter(org.vertx.java.core.http.HttpServer, {
//    requestHandler: function(handler) {
//      var hndlr = new org.vertx.java.core.Handler({
//        handle: function(sock) {
//          handler.handle(sock);
//        }
//      });
//      this.super$requestHandler(hndlr);
//      return this;
//    },
//    websocketHandler: function(handler) {
//      var hndlr = new org.vertx.java.core.Handler({
//        handle: function(ws) {
//          handler.handle(ws);
//        },
//        accept: function(path) {
//          return handler.accept(path);
//        }
//      });
//      this.super$websocketHandler(hndlr);
//      return this;
//    }
//  });
  return new org.vertx.java.core.http.HttpServer();
}

vertx.HttpClient = function() {
  return new org.vertx.java.core.http.HttpClient();
}

vertx.RouteMatcher = function() {
  return new org.vertx.java.core.http.RouteMatcher();
}
