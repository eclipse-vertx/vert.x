load('vertx.js');

var eb = vertx.EventBus;
var log = vertx.logger;

var handler = function(order, replier) {
  log.info('Received order in order manager ' + JSON.stringify(order));
  var sessionID = order.sessionID;
  eb.send('demo.authMgr.validate', { sessionID: sessionID }, function(reply) {
    if (reply.status === 'ok') {
      log.info("Logged in ok");
      // Get the email address for the order
      var username = reply.username;
      eb.send('demo.persistor', {action:'findone', collection:'users', matcher: {username: username}},
        function(reply) {
          if (reply.status === 'ok') {
            replier({status: 'ok'});

            // Send an email
            sendEmail(reply.result.email, order.items);

          } else {
            log.warn('Failed to persist order');
          }
        });
    } else {
      // Invalid session id
      log.warn('invalid session id');
    }
  });
}

function sendEmail(email, items) {

  log.info("sending email to " + email);

  var body = 'Thank you for your order\n\nYou bought:\n\n';
  var totPrice = 0.0;
  for (var i = 0; i < items.length; i++) {
    var quant = items[i].quantity;
    var album = items[i].album;
    var linePrice = quant * album.price;
    totPrice += linePrice;
    body = body.concat(quant, ' of ', album.title, ' at $' ,album.price.toFixed(2),
                       ' Line Total: $', linePrice.toFixed(2), '\n');
  }
  body = body.concat('\n', 'Total: $', totPrice.toFixed(2));

  var msg = {
    from: 'vToons@localhost',
    to: email,
    subject: 'Thank you for your order',
    body: body
  };

  eb.send('demo.mailer', msg);

  log.info("sent email");
}

var address = "demo.orderMgr";
eb.registerHandler(address, handler);

function vertxStop() {
  eb.unregisterHandler(address, handler);
}