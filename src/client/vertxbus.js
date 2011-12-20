var VertxBus = function(url, options) {
  var that = this;

  that.url = url;
  that.options = options;
  that.readyState = VertxBus.CONNECTING;
  that.sockJSConn = new SockJS(url, options);
  that.handlerMap = {};

  that.send = function(address, data, replyHandler) {
    console.log("Sending message");

    if (that.sockJSConn.readyState != SockJS.OPEN) {
      throw new Error('INVALID_STATE_ERR');
    }

    var msg;

    if (!replyHandler) {

      console.log("No reply handler");

      msg = { "type" : "send",
              "address": address,
              "body": data };
      var str = JSON.stringify(msg);
      console.log("Sending stringified json:" + str);
      that.sockJSConn.send(str);
    } else {
      msg = { "type" : "send",
              "address": address,
              "body": data,
              "reply": "true"};

      var wrapped = function(msg) {
        that.unregisterHandler(address, me);
        replyHandler(msg);
      };

      wrapped.me = wrapped;

      that.registerHandler(address, wrapped);

      var str = JSON.stringify(msg);

      that.sockJSConn.send(str);
    }

  }

  that.registerHandler = function(address, handler) {

    var handlers = that.handlerMap["address"];
    if (handlers == undefined) {
      handlers = [handler];
      that.handlerMap["address"] = handlers;

      // First handler for this address so we should register the connection
      var msg = { "type" : "register",
                "address": address };
      that.sockJSConn.send(JSON.stringify(msg));

    } else {
      handlers[handlers.length] = handler;
    }
  }

  that.unregisterHandler = function(address, handler) {

    var handlers = that.handlerMap["address"];
    if (handlers != undefined) {
      var idx = handlers.indexOf(handler);
      if (idx != -1) handlers.splice(idx, 1);
      if (handlers.length == 0) {
        // No more local handlers so we should unregister the connection

        var msg = { "type" : "unregister",
                "address": address};
        that.sockJSConn.send(JSON.stringify(msg));
      }
    }

  }

  that.onopen = function(handler) {
    that.sockJSConn.onopen = handler;
  }

  that.onclose = function(handler) {
    that.sockJSConn.onclose = handler;
  }

  that.sockJSConn.onmessage = function(e) {

    var msg = e.data;

    console.log("Got raw msg:" + msg + " that is " + that);

    var json = JSON.parse(msg);
    var address = json["address"];
    var body = json["body"];
    var handlers = that.handlerMap["address"];
    if (handlers != undefined) {
      for (var i  = 0; i < handlers.length; i++) {
        handlers[i](body);
      }
    }
  }

  alert("in VertxBus");
}



