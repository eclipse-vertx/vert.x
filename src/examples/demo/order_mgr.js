load('vertx.js');

var eb = vertx.EventBus;

var log = vertx.getLogger();

var handler = function(order, replier) {
  log.info('Received order in order manager ' + JSON.stringify(order));
  var sessionID = order.sessionID;
  eb.send('demo.authMgr.validate', { sessionID: sessionID }, function(reply) {
    if (reply.status === 'ok') {
      // Get the email address for the order
      var username = reply.username;
      eb.send('demo.persistor', {action:'findone', collection:'users', matcher: {username: username}},
        function(reply) {
          if (reply.status === 'ok') {
            var email = reply.result.email;
            // Send on the message
            delete order.sessionID;
            order.email = email;
            eb.send('demo.orderQueue', order, function(reply) {
              replier(reply);
            })
          }
        });
    } else {
      // Invalid session id
      log.info('invalid session id');
    }
  });
}

var address = "demo.orderMgr";

eb.registerHandler(address, handler);

function vertxStop() {
  eb.unregisterHandler(address, handler);
}

