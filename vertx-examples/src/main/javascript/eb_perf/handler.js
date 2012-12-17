load('vertx.js')

console.log("Wibble");

var eb = vertx.eventBus;

var address = 'example.address'
var creditsAddress = 'example.credits'

var batchSize = 10000;

var received = 0
var count = 0

var start = null;

// Start the handler first, then the sender

var handler = function(message) {
  received++;
  if (received == batchSize) {
    eb.send(creditsAddress, null);
    received = 0;
  }
  count++;
  if (count % batchSize == 0) {
    // stdout.println("Received " + count);
    if (start == null) {
      start = new Date();
    } else {
      var now = new Date();
      var elapsed = now.getTime() - start.getTime();
      var rate = 1000 * (count + 0.0) / (elapsed);
      stdout.println("rate: " + rate + " msgs/sec");
    }
  }
}

eb.registerHandler(address, handler);

eb.send(creditsAddress, null);

function vertxStop() {
  eb.unregisterHandler(address, handler);
}

console.log("Started");

