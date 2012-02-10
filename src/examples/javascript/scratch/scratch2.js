load('vertx.js');

log.println("in scratch");


var WrappedServer = function() {
  var j_server = new org.vertx.MyServer();

  var that = this;

  this.requestHandler = function(handler) {
    var wrappedHandler = function(req) {

      req.__proto__ = {
        foo: function() {
          log.println("In foo!");
        }
      }

      handler(req);
    }

    j_server.requestHandler(wrappedHandler);
  };

  this.callHandler = function() {
    j_server.callHandler();
  }

  this.callHandlerWithObject = function(obj) {
    j_server.callHandlerWithObject(obj);
  }
}

var server = new WrappedServer();

var handler1 = function(req) {
  log.println("in handler1");
  req.foo();

  // Now replace the handler with a Java handler

  var j_handler = new org.vertx.HandlerProxy(function(req) {
    log.println("in handler2");
    req.foo();
  });

  server.requestHandler(j_handler);

  server.callHandlerWithObject(req);

}

server.requestHandler(handler1);

server.callHandler();


