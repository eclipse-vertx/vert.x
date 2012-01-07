var vertx = vertx || new (function() {

  var that = this;

  var NetSocket = function(jSocket) {
    var that = this;

    that.dataHandler = function(handler) {
      jSocket.dataHandler(function(buff) {
        handler(buff);
      });
    }

    that.write = function(buff) {
      jSocket.write(buff);
    }
  };

  that.NetServer = function() {

    var that = this;
    var jServer = new org.vertx.java.core.net.NetServer();

    that.listen = function(port, host) {
      jServer.listen(port, host);
    };

    that.connectHandler = function(handler) {
      jServer.connectHandler(function(jSocket) {
        handler(new NetSocket(jSocket));
      });
    }
  };

  that.NetClient = function() {
    var that = this;

    var jClient = new org.vertx.java.core.net.NetClient();

    that.connect = function(port, host, handler) {
      jClient.connect(port, host, function(jSocket) {
        handler(new NetSocket(jSocket));
      });
    }

    that.close = function() {
      jClient.close();
    }

  };

  that.Buffer = function(p) {
    return org.vertx.java.core.buffer.Buffer.create(p);
  }


})();