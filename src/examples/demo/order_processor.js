load('vertx.js');

var eb = vertx.EventBus;

var id = vertx.generateUUID();

var handler = function(order, replier) {
  log.println('Received order for processing');

  var email = order.email;
  var items = order.items;

  // Send a confirmation email

  var body = 'Thank you for your order\nYou bought:\n\n';
  for (var i = 0; i < items.length; i++) {
    body = body.concat(items[i].title, ' at £' ,items[i].price, '\n');
  }

  var msg = {
    from: 'vToons@localhost',
    to: email,
    subject: 'Thank you for your order',
    body: body
  };

  eb.send('demo.mailer', msg);

  replier({});

  log.println("Order successfully processed");
}

eb.registerHandler(id, handler);

eb.send('demo.orderQueue.register', {
  processor: id
});

function vertxStop() {
  eb.unregisterHandler(id, handler);
  eb.send('demo.orderQueue.unregister', {
    processor: id
  });
}

