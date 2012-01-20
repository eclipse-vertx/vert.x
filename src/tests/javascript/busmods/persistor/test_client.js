load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.EventBus;

function testSimple() {

  // First delete everything
  eb.send({
    address: 'testPersistor',
    collection: 'testcoll',
    action: 'delete',
    matcher: {}
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });

  // Insert something

  eb.send({
    address: 'testPersistor',
    collection: 'testcoll',
    action: 'save',
    document: {
      name: 'tim',
      age: 40,
      pi: 3.14159,
      male: true
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });

  // Find it

  eb.send({
    address: 'testPersistor',
    collection: 'testcoll',
    action: 'find',
    matcher: {
      name: 'tim'
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(reply.results.length === 1);
    var res = reply.results[0];
    tu.azzert(res.name === 'tim');
    tu.azzert(res.age === 40);
    tu.azzert(res.pi === 3.14159);
    tu.azzert(res.male === true);
    tu.testComplete();
  });
}


tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}