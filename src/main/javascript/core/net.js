var vertx = vertx || {};

vertx.NetServer = function() {

  var that = this;
  var jServer = new org.vertx.java.core.net.NetServer();

  that.listen = function(port, host) {
    jServer.listen(port, host);
  };

  var NetSocket = function(jSocket) {
    var that = this;

    that.dataHandler = function(handler) {
      jSocket.dataHandler(new org.vertx.java.core.Handler({
        handle: function(buff) {
          handler(buff);
        }
      }));
    }

    that.write = function(buff) {
      jSocket.write(buff);
    }
  }

  that.connectHandler = function(handler) {
    jServer.connectHandler(new org.vertx.java.core.Handler({
      handle: function(jSocket) {
        handler(new NetSocket(jSocket));
      }
    }));
  }

}

// TODO hide this in a module

vertx.Buffer = function(param1, param2) {

  if (typeof param == "number") {
    var jBuffer = new org.vertx.java.core.buffer.Buffer.create(param);
  } else if (typeof param1 == "string") {
    var enc = param2 || "UTF-8";
    var jBuffer = new org.vertx.java.core.buffer.Buffer.create(param1, enc);
  }

  var that = this;

  //etc


}