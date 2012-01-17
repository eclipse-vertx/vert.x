load('test_utils.js')
load('core/net.js')

var tu = new TestUtils();

function testEventBus() {

  var eb = vertx.EventBus;

  var address = 'foo-address';

  var sent = {
    address : address,
    price : 23.45,
    name : 'tim'
  }

  eb.registerHandler(address, function MyHandler(msg, replier) {
    tu.azzert(sent.address === msg.address);
    tu.azzert(sent.price === msg.price);
    tu.azzert(sent.name === msg.name);
    eb.unregisterHandler(address, MyHandler);
    tu.testComplete();
  });

  eb.send(sent);
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}