load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var client;

function testHTTP() {

  client = new vertx.HttpClient();
  var request = client.setPort(8080).setHost('localhost').put('some-uri?param1=abc&param2=def', function(resp) {
    tu.azzert(resp.statusCode == 200);

    var headers = resp.headers();
    tu.azzert(headers['header1'] === "value1");
    tu.azzert(headers['header2'] === "value2");
    resp.bodyHandler(function(body) {
      tu.azzert(body.toString() == 'Hello World');

      var trailers = resp.trailers();
      tu.azzert(trailers['trailer1'] === "value1");
      tu.azzert(trailers['trailer2'] === "value2");

      tu.testComplete();
    });
  });
  request.setChunked(true);
  request.putHeader("header1", "value1");
  request.putHeader("header2", "value2");
  request.write('Hello World');
  request.end();
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  client.close();
  tu.unregisterAll();
  tu.appStopped();
}