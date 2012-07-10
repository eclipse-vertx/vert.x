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

vertx.EventBus = function(url, options) {

  var that = this;
  var sockJSConn = new SockJS(url, options);
  var handlerMap = {};
  var replyHandlers = {};
  var state = vertx.EventBus.CONNECTING;

  that.onopen = null;
  that.onclose = null;

  that.send = function(address, message, replyHandler) {
    sendOrPub("send", address, message, replyHandler)
  }

  that.publish = function(address, message, replyHandler) {
    sendOrPub("publish", address, message, replyHandler)
  }

  that.registerHandler = function(address, handler) {
    checkSpecified("address", 'string', address);
    checkSpecified("handler", 'function', handler);
    checkOpen();
    var handlers = handlerMap[address];
    if (!handlers) {
      handlers = [handler];
      handlerMap[address] = handlers;
      // First handler for this address so we should register the connection
      var msg = { type : "register",
                  address: address };
      sockJSConn.send(JSON.stringify(msg));
    } else {
      handlers[handlers.length] = handler;
    }
  }

  that.unregisterHandler = function(address, handler) {
    checkSpecified("address", 'string', address);
    checkSpecified("handler", 'function', handler);
    checkOpen();
    var handlers = handlerMap[address];
    if (handlers) {
      var idx = handlers.indexOf(handler);
      if (idx != -1) handlers.splice(idx, 1);
      if (handlers.length == 0) {
        // No more local handlers so we should unregister the connection

        var msg = { type : "unregister",
                    address: address};
        sockJSConn.send(JSON.stringify(msg));
        delete handlerMap[address];
      }
    }
  }

  that.close = function() {
    checkOpen();
    state = vertx.EventBus.CLOSING;
    sockJSConn.close();
  }

  that.readyState = function() {
    return state;
  }

  sockJSConn.onopen = function() {
    state = vertx.EventBus.OPEN;
    if (that.onopen) {
      that.onopen();
    }
  };

  sockJSConn.onclose = function() {
    state = vertx.EventBus.CLOSED;
    if (that.onclose) {
      that.onclose();
    }
  };

  sockJSConn.onmessage = function(e) {
    var msg = e.data;
    var json = JSON.parse(msg);
    var body = json.body;
    var replyAddress = json.replyAddress;
    var address = json.address;
    var replyHandler;
    if (replyAddress) {
      replyHandler = function(reply, replyHandler) {
        // Send back reply
        that.send(replyAddress, reply, replyHandler);
      };
    }
    var handlers = handlerMap[address];
    if (handlers) {
      // We make a copy since the handler might get unregistered from within the
      // handler itself, which would screw up our iteration
      var copy = handlers.slice(0);
      for (var i  = 0; i < copy.length; i++) {
        copy[i](body, replyHandler);
      }
    } else {
      // Might be a reply message
      var handler = replyHandlers[address];
      if (handler) {
        delete replyHandlers[replyAddress];
        handler(body, replyHandler);
      }
    }
  }

  function sendOrPub(sendOrPub, address, message, replyHandler) {
    checkSpecified("address", 'string', address);
    checkSpecified("message", 'object', message);
    checkSpecified("replyHandler", 'function', replyHandler, true);
    checkOpen();
    var envelope = { type : sendOrPub,
                     address: address,
                     body: message };
    if (replyHandler) {
      var replyAddress = makeUUID();
      envelope.replyAddress = replyAddress;
      replyHandlers[replyAddress] = replyHandler;
    }
    var str = JSON.stringify(envelope);
    sockJSConn.send(str);
  }

  function checkOpen() {
    if (state != vertx.EventBus.OPEN) {
      throw new Error('INVALID_STATE_ERR');
    }
  }

  function checkSpecified(paramName, paramType, param, optional) {
    if (!optional && !param) {
      throw new Error("Parameter " + paramName + " must be specified");
    }
    if (param && typeof param != paramType) {
      throw new Error("Parameter " + paramName + " must be of type " + paramType);
    }
  }

  function isFunction(obj) {
    return !!(obj && obj.constructor && obj.call && obj.apply);
  }

  function makeUUID(){return"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx"
      .replace(/[xy]/g,function(a,b){return b=Math.random()*16,(a=="y"?b&3|8:b|0).toString(16)})}

}

vertx.EventBus.CONNECTING = 0;
vertx.EventBus.OPEN = 1;
vertx.EventBus.CLOSING = 2;
vertx.EventBus.CLOSED = 3;