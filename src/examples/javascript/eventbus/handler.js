load('vertx.js')

var eb = vertx.EventBus;

var log = vertx.logger;

var handler = function(message) {
  log.info('received the message ' + message);
}

eb.registerHandler("example.address", handler);
log.info("registered handler");

function vertxStop() {
  eb.unregisterHandler(handler);
}

