/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var vertx = vertx || {};

if (!vertx.eventBus) {
  vertx.eventBus = new (function() {
    var that = this;

    var handlerMap = {};

    var jEventBus = org.vertx.java.deploy.impl.VertxLocator.vertx.eventBus();

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

          handler(body, function(reply, replyHandler) {
            if (typeof reply === 'undefined') {
              throw "Reply message must be specified";
            }
            reply = convertMessage(reply);
            if (replyHandler) {
              var wrapped = wrappedHandler(replyHandler);
              jMsg.reply(reply, wrapped);
            } else {
              jMsg.reply(reply);
            }
          })
        }
      });
    }

    function registerHandler(address, handler, localOnly) {
      checkHandlerParams(address, handler);

      var wrapped = wrappedHandler(handler);

      // This is a bit more complex than it should be because we have to wrap the handler - therefore we
      // have to keep track of it :(
      handlerMap[handler] = wrapped;

      if (localOnly) {
        return jEventBus.registerLocalHandler(address, wrapped);
      } else {
        return jEventBus.registerHandler(address, wrapped);
      }
    }

    that.registerLocalHandler = function(address, handler) {
      return registerHandler(address, handler, true);
    };

    that.registerHandler = function(address, handler) {
      return registerHandler(address, handler, false);
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
          // If null then we just wrap it as an empty JSON message
          // We don't do this if it's a Java class (it has the getClass) method
          // since it may be a Buffer which we want to let through
          if (message == null || typeof message.getClass === "undefined") {
            // Not a Java object - assume JSON message
            message = new org.vertx.java.core.json.JsonObject(JSON.stringify(message));
          }
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

}
