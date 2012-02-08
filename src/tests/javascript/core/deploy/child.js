load('test_utils.js')
load('vertx.js')

var eb = vertx.EventBus;

eb.send("test-handler", "started");

function vertxStop() {
  eb.send("test-handler", "stopped");
}