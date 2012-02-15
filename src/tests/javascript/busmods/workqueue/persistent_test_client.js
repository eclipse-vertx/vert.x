load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.EventBus;

function testPersistentWorkQueue() {

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

function deleteAll() {
  eb.send('test.persistor', {
    collection: 'work',
    action: 'delete',
    matcher: {}
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });
}

tu.registerTests(this);

var persistorConfig = {address: 'test.persistor', db_name: 'test_db'}
vertx.deployWorkerVerticle('busmods/mongo_persistor.js', persistorConfig, 1, function() {
  deleteAll();
  var queueConfig = {address: 'test.orderQueue', persistor_address: 'test.persistor', collection: 'work'}
  vertx.deployWorkerVerticle('busmods/work_queue.js', queueConfig, 1, function() {
    tu.appReady();
  });
});



function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}