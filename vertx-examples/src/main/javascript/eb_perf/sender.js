load('vertx.js')

var eb = vertx.eventBus;

var address = 'example.address'
var creditsAddress = 'example.credits'

var batchSize = 10000;

var handler = function() {
  credits += batchSize;
  sendMessage();
}

eb.registerHandler(creditsAddress, handler);

var credits = 0;
var count = 0

sendMessage();

function sendMessage() {
  for (var i = 0; i < batchSize / 2; i++) {
    if (credits > 0) {
      credits--;
      eb.send(address, "some-message");
      // stdout.println("sent message " + count);
      count++;
    }
    else {
      return;
    }
  }
  vertx.runOnLoop(sendMessage);
}

function vertxStop() {
  eb.unregisterHandler(creditsAddress, handler);
}

console.log("Started");
