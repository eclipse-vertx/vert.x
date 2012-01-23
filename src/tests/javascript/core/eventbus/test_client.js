load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

// Most testing occurs in the Java tests

var eb = vertx.EventBus;
var address = 'foo-address';

var sent = {
  price : 23.45,
  name : 'tim'
};

var emptySent = {
  address : address
};

var reply = {
  desc: "approved",
  status: 123
}

function assertSent(msg) {
  tu.azzert(sent.price === msg.price);
  tu.azzert(sent.name === msg.name);
}


function assertReply(rep) {
  tu.azzert(reply.desc === rep.desc);
  tu.azzert(reply.status === rep.status);
}

function testSimple() {

  var handled = false;
  eb.registerHandler(address, function MyHandler(msg, replier) {
    tu.checkContext();
    tu.azzert(!handled);
    assertSent(msg);
    eb.unregisterHandler(address, MyHandler);
    handled = true;
    tu.testComplete();
  });

  eb.send(address, sent);
}

function testEmptyMessage() {

  var handled = false;
  eb.registerHandler(address, function MyHandler(msg, replier) {
    tu.checkContext();
    tu.azzert(!handled);
    eb.unregisterHandler(address, MyHandler);
    handled = true;
    tu.testComplete();
  });

  eb.send(address, emptySent);
}


function testUnregister() {

  var handled = false;
  eb.registerHandler(address, function MyHandler(msg, replier) {
    tu.checkContext();
    tu.azzert(!handled);
    assertSent(msg);
    eb.unregisterHandler(address, MyHandler);
    // Unregister again - should do nothing
    eb.unregisterHandler(address, MyHandler);
    handled = true;
    // Wait a little while to allow any other messages to arrive
    vertx.setTimer(100, function() {
      tu.testComplete();
    })
  });

  for (var i = 0; i < 2; i++) {
    eb.send(address, sent);
  }
}

function testWithReply() {

  var handled = false;
  eb.registerHandler(address, function MyHandler(msg, replier) {
    tu.checkContext();
    tu.azzert(!handled);
    assertSent(msg);
    eb.unregisterHandler(address, MyHandler);
    handled = true;
    replier(reply);
  });

  eb.send(address, sent, function(reply) {
    tu.checkContext();
    assertReply(reply);
    tu.testComplete();
  });
  eb.send(address, sent);
}

function testEmptyReply() {

  var handled = false;
  eb.registerHandler(address, function MyHandler(msg, replier) {
    tu.checkContext();
    tu.azzert(!handled);
    assertSent(msg);
    eb.unregisterHandler(address, MyHandler);
    handled = true;
    replier({});
  });

  eb.send(address, sent, function(reply) {
    tu.checkContext();
    tu.testComplete();
  });
  eb.send(address, sent);
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}