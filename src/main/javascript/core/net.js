var vertx = vertx || {};

vertx.NetServer = function() {
  /*
  There's a bug in Rhino which means we can't pass the handler function directly into the Java object
  connectHandler method, since, when closing the server, Rhino attempts to call equals() on it, but instead
  ends up calling the handler itself, then barfs.
  To workaround this we need to wrap the server and override the connectHandler method, then wrap the handler
  itself and pass that in.
  It's ugly but it works.
   */
  var server = new JavaAdapter(org.vertx.java.core.net.NetServer, {
    connectHandler: function(handler) {
      var hndlr = new org.vertx.java.core.Handler({
        handle: function(sock) {
          handler.handle(sock);
        }
      });
      this.super$connectHandler(hndlr);
    }
  });
  return server;
}

vertx.NetClient = function() {
  return new org.vertx.java.core.net.NetClient();
}



//  var NetSocket = function(jSocket) {
//    var that = this;
//
//    that.dataHandler = function(handler) {
//      jSocket.dataHandler(handler);
//    };
//
//    that.drainHandler = function(handler) {
//      jSocket.drainHandler(handler);
//    };
//
//    that.write = function(buff) {
//      jSocket.write(buff);
//    };
//
//    that.pause = function() {
//      jSocket.pause();
//    };
//
//    that.resume = function() {
//      jSocket.resume();
//    };
//
//    that.writeQueueFull = function() {
//      jSocket.writeQueueFull();
//    }
//  };

//  that.NetServer = function() {
//
//    var that = this;
//    var jServer = new org.vertx.java.core.net.NetServer();
//
//    that.listen = function(port, host) {
//      jServer.listen(port, host);
//    };
//
//    that.connectHandler = function(handler) {
//      jServer.connectHandler(handler);
//    }
//
//    that.close = function(doneHandler) {
//      if (doneHandler) {
//        jServer.close(doneHandler);
//      } else {
//        jServer.close();
//      }
//    }
//  };

  //  that.NetClient = function() {
//    var that = this;
//
//    var jClient = new org.vertx.java.core.net.NetClient();
//
//    that.connect = function(port, host, handler) {
//      jClient.connect(port, host, function(jSocket) {
//        handler(new NetSocket(jSocket));
//      });
//    }
//
//    that.close = function() {
//      jClient.close();
//    }
//
//  };