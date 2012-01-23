load('vertx.js');

var eb = vertx.EventBus;

var id = vertx.generateUUID();

var handler = function(order, replier) {
  log.println('Received order for processing');

  vertx.setTimer(500, function() {

    // Send a mail

    var msg = {
      address: "demo.mailer",
      from: 'tim@localhost',
      to: 'tim@localhost',
      subject: 'Thank you for your order',
      body: 'blah blah blah'
    }

    eb.send(msg);

    replier({});

    log.println("Order successfully processed");

  })

}

eb.registerHandler(id, handler);

eb.send({
  address: "demo.orderQueue",
  action: "register",
  processor: id
});

function vertxStop() {
  eb.unregisterHandler(id, handler);
}

