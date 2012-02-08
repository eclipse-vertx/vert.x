var vertx = vertx || {};

if (!vertx.EventBus) {
  vertx.EventBus = new (function() {
    var that = this;

    var handlerMap = {};

    var jEventBus = org.vertx.java.core.eventbus.EventBus.instance;

    // Get ref to the Java class of the JsonObject
    //var jsonObjectClass = new org.vertx.java.core.json.JsonObject('{}').getClass();

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

    function wrappedHandler(handler) {
      return new org.vertx.java.core.Handler({
        handle: function(jMsg) {
          var body = jMsg.body;

          if (typeof body === 'object') {
            // Convert to JS JSON object
            if (jMsg.body) {
              body = JSON.parse(jMsg.body.encode());
            } else {
              body = undefined;
            }
          }

          handler(body, function(reply) {
            if (typeof reply === 'undefined') {
              throw "Reply message must be specified";
            }
            reply = convertMessage(reply);
            jMsg.reply(reply);
          })
        }
      });
    }

    that.registerHandler = function(address, handler) {
      checkHandlerParams(address, handler);

      var wrapped = wrappedHandler(handler);

      // This is a bit more complex than it should be because we have to wrap the handler - therefore we
      // have to keep track of it :(
      handlerMap[handler] = wrapped;

      jEventBus.registerHandler(address, wrapped);
    };

    that.unregisterHandler = function(address, handler) {
      checkHandlerParams(address, handler);
      var wrapped = handlerMap[handler];
      if (wrapped) {
        jEventBus.unregisterHandler(address, wrapped);
        delete handlerMap[handler];
      }
    };


    function convertMessage(message) {
      var msgType = typeof message;
      switch (msgType) {
        case 'string':
        case 'boolean':
        case 'undefined':
          break;
        case 'number':
          message = new java.lang.Double(message);
          break;
        case 'object':
          // Assume JSON message
          message = new org.vertx.java.core.json.JsonObject(JSON.stringify(message));
          break;
        default:
          throw 'Invalid type for message: ' + msgType;
      }
      return message;
    }

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
      message = convertMessage(message);
      if (replyHandler) {
        var wrapped = wrappedHandler(replyHandler);
        jEventBus.send(address, message, wrapped);
      } else {
        jEventBus.send(address, message);
      }
    };

  })();

  vertx.SockJSBridgeHandler = function() {
    var jHandler = org.vertx.java.core.eventbus.SockJSBridgeHandler();
    var server = new org.vertx.java.core.Handler({
      handle: function(sock) {
        jHandler.handle(sock);
      }
    });
    server.addPermitted = function() {
      for (var i = 0; i < arguments.length; i++) {
        var match = arguments[i];
        var json_str = JSON.stringify(match);
        var jJson = new org.vertx.java.core.json.JsonObject(json_str);
        jHandler.addPermitted(jJson);
      }
    }
    return server;
  }
}
