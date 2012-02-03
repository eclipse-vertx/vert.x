load('vertx.js')

var eb = vertx.EventBus;

var handler = function(message) {
  log.println('received the message ' + message);
}

eb.registerHandler("example.address", handler);
log.println("registered handler");

function vertxStop() {
  eb.unregisterHandler(handler);
}

