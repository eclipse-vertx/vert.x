load('vertx.js');

log.println("in scratch");

var WrappedServer = function() {
  var j_server = new org.vertx.MyServer();

  var that = this;

  this.requestHandler = function(handler) {
    if (handler) {

      // Wrap the handler

      var wrappedHandler = function(req) {

        req.__proto__ = {
          foo: function() {
            log.println("In foo!");
          }
        }

        handler(req);
      }

      j_server.requestHandler(wrappedHandler);
    }
    return that;
  };

  this.websocketHandler = function(handler) {
    if (handler) {
      j_server.websocketHandler(handler);
    }
    return that;
  };

  this.close = function(handler) {
    if (handler) {
      j_server.close(handler);
    } else {
      j_server.close();
    }
  };

  this.listen = function(port, host) {
    if (host) {
      j_server.listen(port, host);
    } else {
      j_server.listen(port);
    }
  }

  this.callHandler = function() {
    j_server.callHandler();
  }

}

//var wrappedServer = new JavaAdapter(org.vertx.MyServer, {
//
//  //Overide the requestHandler function
//
//  requestHandler: function(handler) {
//
//    // Call the original requestHandler method passing in a wrapped handler
//    // which adds the foo function to the request
//
//    this.super$requestHandler(function(j_req) {
//
//        var proto = {
//            foo: function() {
//                log.println("In foo!");
//            }
//        };
//
//        j_req.__proto__ = proto;
//
//
//        // I try to execute this:
//
//        // handler(j_req);
//
//        // But it doesn't work since the handler var seems already to have been converted to
//        // a Java object - typeof handler returns object, not function
//
//        log.println("typeof req: " + typeof j_req);
//
//
//        //So instead I call handle on it:
//
//        handler.handle(j_req);
//
//        //This does result in the JS handler being called, however it doesn't seem to look at the proto
//        //when resolving functions.
//
//    });
//
//  }
//});

var wrapped = new WrappedServer();

wrapped.requestHandler(function(req) {
    // This throws an exception  - can't find function foo
    req.foo();
})

wrapped.callHandler();