load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.EventBus;

function testWorkQueue() {
  var numMessages = 100;

  var count = 0;
  var doneHandler = function() {
    if (++count == numMessages) {
      eb.unregisterHandler("done", doneHandler);
      tu.testComplete();
    }
  };

  eb.registerHandler("done", doneHandler);

  for (var i = 0; i < numMessages; i++) {
    eb.send('test.orderQueue', {
      blah: "somevalue: " + i
    })
  }

}

tu.registerTests(this);
var queueConfig = {address: 'test.orderQueue'}
var queueID = vertx.deployWorkerVerticle('busmods/work_queue.js', queueConfig, 1, function() {
  tu.appReady();
});

function vertxStop() {
  tu.unregisterAll();
  vertx.undeployVerticle(queueID, function() {
    tu.appStopped();
  });
}