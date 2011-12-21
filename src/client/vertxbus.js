/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Tim Fox
 * Description: Client side JavaScript for the vert.x event bus. Requires SockJS
 *
 */
var VertxBus = function(url, options) {

  // I use Crockford style to hide all private data

  var that = this;
  var sockJSConn = new SockJS(url, options);
  var handlerMap = {};
  var state = VertxBus.CONNECTING;

  that.onopen = null;
  that.onclose = null;

  that.send = function(address, data, replyHandler) {
    checkSpecified("address", address);
    checkSpecified("data", data);
    if (typeof data != "string") {
      throw new Error("data parameter must be a string");
    }
    if (replyHandler && !isFunction(replyHandler)) {
      throw new Error("replyHandler must be a function");
    }
    checkOpen();
    var msgID = makeUUID();
    var msg = { "type" : "send",
              "messageID": msgID,
              "address": address,
              "body": data };
    if (replyHandler) {
      var replyAddress = makeUUID();
      msg["replyAddress"] = replyAddress;
      function wrapped(msg) {
        that.unregisterHandler(replyAddress, wrapped);
        replyHandler(msg);
      };
      that.registerHandler(replyAddress, wrapped);
    }
    var str = JSON.stringify(msg);
    sockJSConn.send(str);
    return msgID;
  }

  that.registerHandler = function(address, handler) {
    checkSpecified("address", address);
    if (handler && !isFunction(handler)) {
      throw new Error("handler must be a function");
    }
    checkOpen();
    var handlers = handlerMap[address];
    if (handlers == undefined) {
      handlers = [handler];
      handlerMap[address] = handlers;
      // First handler for this address so we should register the connection
      var msg = { "type" : "register",
                "address": address };
      sockJSConn.send(JSON.stringify(msg));
    } else {
      handlers[handlers.length] = handler;
    }
  }

  that.unregisterHandler = function(address, handler) {
    if (handler && !isFunction(handler)) {
      throw new Error("handler must be a function");
    }
    checkOpen();
    var handlers = handlerMap[address];
    if (handlers != undefined) {
      var idx = handlers.indexOf(handler);
      if (idx != -1) handlers.splice(idx, 1);
      if (handlers.length == 0) {
        // No more local handlers so we should unregister the connection

        var msg = { "type" : "unregister",
                "address": address};
        sockJSConn.send(JSON.stringify(msg));
        delete handlerMap[address];
      }
    }
  }

  that.close = function() {
    checkOpen();
    state = VertxBus.CLOSING;
    sockJSConn.close();
  }

  that.readyState = function() {
    return state;
  }

  sockJSConn.onopen = function() {
    state = VertxBus.OPEN;
    if (that.onopen) {
      that.onopen();
    }
  };

  sockJSConn.onclose = function() {
    state = VertxBus.CLOSED;
    if (that.onclose) {
      that.onclose();
    }
  };

  sockJSConn.onmessage = function(e) {
    var msg = e.data;
    var json = JSON.parse(msg);
    var address = json["address"];
    var body = json["body"];
    var messageID = json["messageID"];
    var replyAddress = json["replyAddress"];
    var handlers = handlerMap[address];
    if (handlers) {
      for (var i  = 0; i < handlers.length; i++) {
        if (replyAddress) {
          handlers[i](body, function(data) {
            // Send back reply
            that.send(replyAddress, data);
          });
        } else {
          handlers[i](body);
        }
      }
    }
  }

  function checkOpen() {
    if (state != VertxBus.OPEN) {
      throw new Error('INVALID_STATE_ERR');
    }
  }

  function checkSpecified(paramName, param) {
    if (!param) {
      throw new Error(paramName + " parameter must be specified");
    }
  }

  function isFunction(obj) {
    return !!(obj && obj.constructor && obj.call && obj.apply);
  }

  function makeUUID(){return"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx"
      .replace(/[xy]/g,function(a,b){return b=Math.random()*16,(a=="y"?b&3|8:b|0).toString(16)})}

}

VertxBus.CONNECTING = 0;
VertxBus.OPEN = 1;
VertxBus.CLOSING = 2;
VertxBus.CLOSED = 3;




