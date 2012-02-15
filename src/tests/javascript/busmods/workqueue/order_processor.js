load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var eb = vertx.EventBus;

var id = vertx.generateUUID();

var dontsendAppLifeCycle = vertx.getConfig().dont_send_app_lifecycle;

var handler = function(message, replier) {
  tu.azzert(message.blah != "undefined");
  replier({});
  eb.send('done', {});
};

eb.registerHandler(id, handler);

eb.send('test.orderQueue.register', {
  processor: id
}, function() {
  if (!dontsendAppLifeCycle) {
    tu.appReady();
  }
});

function vertxStop() {
  eb.send('test.orderQueue.unregister', {
    processor: id
  });
  eb.unregisterHandler(id, handler);
  tu.checkContext();
  if (!dontsendAppLifeCycle) {
    tu.appStopped();
  }
}



