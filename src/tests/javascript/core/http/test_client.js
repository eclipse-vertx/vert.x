load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var client;

function testHTTP() {

  client = new vertx.HttpClient();
  client.setPort(8080).setHost('localhost').getNow('some-uri', function(resp) {
    tu.azzert(resp.statusCode == 200);
    resp.bodyHandler(function(body) {
      tu.azzert(body.toString() == 'Hello World');
      tu.testComplete();
    });

  });
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  client.close();
  tu.unregisterAll();
  tu.appStopped();
}