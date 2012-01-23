load('vertx.js');

var eb = vertx.EventBus;

var handler = function(order, replier) {
  log.println('Received order');
  replier({status: 'ok', message: 'your order has been processed'});
}

eb.registerHandler('demo.orderProcessor', handler);

function vertxStop() {
  eb.unregisterHandler('demo.orderProcessor', handler);
}
