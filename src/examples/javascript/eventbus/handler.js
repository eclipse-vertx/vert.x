load('vertx.js')

var eb = vertx.EventBus;

var address = 'example.address'

var handler = function(message) {
  stdout.println('received the message ' + message);
}

eb.registerHandler(address, handler);

function vertxStop() {
  eb.unregisterHandler(address, handler);
}

