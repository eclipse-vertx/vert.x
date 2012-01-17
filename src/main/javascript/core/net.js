var vertx = vertx || new (function() {

  var that = this;

  that.NetServer = function() {
    return new org.vertx.java.core.net.NetServer();
  }

  that.NetClient = function() {
    return new org.vertx.java.core.net.NetClient();
  }

  that.Buffer = function(p) {
    return org.vertx.java.core.buffer.Buffer.create(p);
  }

  that.Pump = function(rs, ws) {
    return new org.vertx.java.core.streams.Pump(rs, ws);
  }

  that.EventBus = new (function() {
    var that = this;

    var jEventBus = org.vertx.java.core.eventbus.EventBus.instance;

    that.registerHandler = function(address, handler) {

      var wrapped = new org.vertx.java.core.Handler({
        handle: function(jMsg) {
           // Null bodies??
          var bodyStr = jMsg.body.toString();
          var json = JSON.parse(bodyStr);
          json.address = address;
          handler(json, function(replyBuffer) {
            jMsg.reply(replyBuffer)
          })
        }
      });

      jEventBus.registerHandler(address, wrapped);
    };


    that.unregisterHandler = function(address, handler) {
    };

    /*
    Message should be a JSON object
    It should have a property "address"
     */
    that.send = function(message) {
      var address = message.address;
      if (!address) {
        throw "The message should be a JSON object with a field 'address'";
      }

      //delete message.address;

      var bodyStr = JSON.stringify(message);
      var body = org.vertx.java.core.buffer.Buffer.create(bodyStr);
      var java_msg = new org.vertx.java.core.eventbus.Message(address, body);
      jEventBus.send(java_msg);
    };

  })();


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



})();