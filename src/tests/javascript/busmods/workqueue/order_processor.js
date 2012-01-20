load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var eb = vertx.EventBus;

var id = vertx.generateUUID();

var handler = function(message, replier) {
  tu.azzert(message.blah != "undefined");
  replier({});
  eb.send({address: "done"});
};

eb.registerHandler(id, handler);

eb.send({
  address: "orderQueue",
  action: "register",
  processor: id
}, function() {
  tu.appReady();
});

function vertxStop() {
  eb.send({
    address: "orderQueue",
    action: "unregister",
    processor: id
  });
  eb.unregisterHandler(id, handler);
  tu.checkContext();
  tu.appStopped();
}



