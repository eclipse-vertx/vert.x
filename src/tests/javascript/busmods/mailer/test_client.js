load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.EventBus;

var user = 'tim@localhost';

function testMailer() {

  var msg = {
    address: "testMailer",
    from: user,
    to: user,
    subject: 'this is the subject',
    body: 'this is the body'
  }

  log.println("sending: " + JSON.stringify(msg));

  eb.send(msg, function(msg) {
    tu.azzert(msg.status == 'ok');
    tu.testComplete();
  });
}

function testMailerError() {
  var msg = {
    address: "testMailer",
    from: "wdok wdqwd qd",
    to: user,
    subject: 'this is the subject',
    body: 'this is the body'
  }

  eb.send(msg, function(msg) {
    tu.azzert(msg.status == 'error');
    tu.testComplete();
  });
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}