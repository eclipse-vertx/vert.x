load('vertx.js');

var eb = vertx.EventBus;

var handler = function(order, replier) {
  log.println('Received order');
  eb.send({action: 'send', address: 'demo.orderQueue', work: order});
  replier({status: 'ok', message: 'your order has been received and will be processed shortly.'});
}

eb.registerHandler('demo.orderManager', handler);

function vertxStop() {
  eb.unregisterHandler('demo.orderManager', handler);
}
