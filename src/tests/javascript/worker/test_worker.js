load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var handler = function(msg, replier) {
  tu.azzert(msg.foo === 'wibble');

  // Trying to create any network clients or servers should fail - workers can only use the event bus

  try {
    new vertx.NetServer();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

   try {
    new vertx.NetClient();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

   try {
    new vertx.HttpServer();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

   try {
    new vertx.HttpClient();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

  replier({eek: 'blurt'});
};

vertx.EventBus.registerHandler('testWorker', handler);

tu.appReady();

function vertxStop() {
  vertx.EventBus.unregisterHandler('testWorker', handler);
  tu.checkContext();
  tu.appStopped();
}
