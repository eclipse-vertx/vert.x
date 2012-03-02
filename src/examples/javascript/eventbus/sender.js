load('vertx.js')

var eb = vertx.EventBus;

var address = 'example.address'
var creditsAddress = 'example.credits'

var handler = function() {
  credits += 1000;
  sendMessage();
}

eb.registerHandler(creditsAddress, handler);

var credits = 1000;
var count = 0

sendMessage();

function sendMessage() {
  for (var i = 0; i < 200; i++) {
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
  vertx.nextTick(sendMessage);
}

function vertxStop() {
  eb.unregisterHandler(creditsAddress, handler);
}
