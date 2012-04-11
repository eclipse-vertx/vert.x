load('vertx.js');

var eb = vertx.eventBus;
var log = vertx.logger;

eb.registerHandler('demo.orderMgr', function(order, replier) {
  validateUser(order, replier);
});

function validateUser(order, replier) {
  eb.send('demo.authMgr.validate', { sessionID: order.sessionID }, function(reply) {
    if (reply.status === 'ok') {
      order.username = reply.username;
      saveOrder(order, replier);
    } else {
      log.warn("Failed to validate user");
    }
  });
}

function saveOrder(order, replier) {
  eb.send('demo.persistor',
      {action:'save', collection: 'orders', document: order}, function(reply) {
    if (reply.status === 'ok') {
      log.info("order successfully processed!");
      replier({status: 'ok'}); // Reply to the front end
    } else {
      log.warn("Failed to save order");
    }
  });
}

