load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.EventBus;

function testWorker() {
  eb.send({
    address: "testWorker",
    foo: "wibble"
  }, function(reply) {
    tu.azzert(reply.eek === 'blurt');
    tu.testComplete();
  });
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}