load('test_utils.js')
load('core/net.js')

var tu = new TestUtils();

var client;

function test1() {

  client = new vertx.NetClient();

  client.connect(8080, 'localhost', function(sock) {

    sock.dataHandler(function(data) {
      //log.println("Got data echoed back");
      tu.testComplete();
    });

    sock.write(new vertx.Buffer('this is a buffer'));

  });

  tu.testComplete();
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  client.close();
  tu.unregisterAll();
  tu.appStopped();
}