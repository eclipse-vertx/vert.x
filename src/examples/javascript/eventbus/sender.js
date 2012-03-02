load('vertx.js')

var eb = vertx.EventBus;

var address = 'example.address'

vertx.setPeriodic(1000, function() {
  stdout.println("Sending message");
  eb.send(address, 'hello world');
});
