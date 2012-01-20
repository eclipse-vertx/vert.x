load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var client;

function test1() {

  client = new vertx.NetClient();

  client.connect(8080, 'localhost', function(sock) {

    sock.dataHandler(function(data) {
      tu.testComplete();
    });

    sock.write(new vertx.Buffer('this is a buffer'));

  });
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  client.close();
  tu.unregisterAll();
  tu.appStopped();
}