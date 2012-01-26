var vertx = vertx || {};

if (!vertx.EventBus) {
  vertx.EventBus = new (function() {
    var that = this;

    var handlerMap = {};

    var jEventBus = org.vertx.java.core.eventbus.EventBus.instance;

    function checkHandlerParams(address, handler) {
      if (!address) {
        throw "address must be specified";
      }
      if (!handler) {
        throw "handler must be specified";
      }
      if (typeof address != "string") {
        throw "address must be a string";
      }
      if (typeof handler != "function") {
        throw "handler must be a function";
      }
    }

    function bufferToStr(jBuffer) {
      var str;
      if (jBuffer.length() == 0) {
        str = "{}";
      } else {
        str = '' + jBuffer.toString();
      }
      return str;
    }

    that.registerHandler = function(address, handler) {
      checkHandlerParams(address, handler);
      var wrapped = new org.vertx.java.core.Handler({
        handle: function(jMsg) {
          var bodyStr = bufferToStr(jMsg.body);
          var json = JSON.parse(bodyStr);
          handler(json, function(reply) {
            if (!reply) {
              throw "Reply message must be specified";
            }
            var bodyStr = JSON.stringify(reply);
            var body = org.vertx.java.core.buffer.Buffer.create(bodyStr);
            jMsg.reply(body);
          })
        }
      });

      // This is a bit more complex than it should be because we have to wrap the handler - therefore we
      // have to keep track of it :(
      handlerMap[handler] = wrapped;

      jEventBus.registerBinaryHandler(address, wrapped);
    };


    that.unregisterHandler = function(address, handler) {
      checkHandlerParams(address, handler);
      var wrapped = handlerMap[handler];
      if (wrapped) {
        jEventBus.unregisterBinaryHandler(address, wrapped);
        delete handlerMap[handler];
      }
    };

    /*
    Message should be a JSON object
    It should have a property "address"
     */
    that.send = function(address, message, replyHandler) {
      if (!address) {
        throw "address must be specified";
      }
      if (typeof address != "string") {
        throw "address must be a string";
      }
      if (replyHandler && typeof replyHandler != "function") {
        throw "replyHandler must be a function";
      }
      var bodyStr = JSON.stringify(message);
      var body = org.vertx.java.core.buffer.Buffer.create(bodyStr);
      var java_msg = new org.vertx.java.core.eventbus.Message(address, body);
      if (replyHandler) {
        var hndlr = new org.vertx.java.core.Handler({
          handle: function(jMessage) {
            var bodyStr = bufferToStr(jMessage.body);
            var json = JSON.parse(bodyStr);
            replyHandler(json);
          }
        });
        jEventBus.sendBinary(java_msg, hndlr);
      } else {
        jEventBus.sendBinary(java_msg);
      }
      message.messageID = '' + java_msg.messageID;
    };

  })();
}

vertx.SockJSBridgeHandler = function() {
  var jHandler = org.vertx.java.core.eventbus.SockJSBridgeHandler();
  var jsonHelper = new org.vertx.java.core.eventbus.JsonHelper();
  var server = new org.vertx.java.core.Handler({
    handle: function(sock) {
      jHandler.handle(sock);
    }
  });
  server.addPermitted = function() {
    for (var i = 0; i < arguments.length; i++) {
      var match = arguments[i];
      var json_str = JSON.stringify(match);
      var jJson = jsonHelper.stringToJson(json_str);
      jHandler.addPermitted(jJson);
    }
  }
  return server;
}
