load('vertx.js');

var eb = vertx.EventBus;

var log = vertx.getLogger();

var id = vertx.generateUUID();

var handler = function(order, replier) {
  log.info('Received order for processing ' + JSON.stringify(order));

  var email = order.email;
  var items = order.items;

  // Send a confirmation email

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

  replier({});

  log.info("Order successfully processed");
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

