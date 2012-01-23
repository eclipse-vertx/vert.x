load('vertx.js')
load('test_utils.js')

var tu = new TestUtils();

var server = new vertx.HttpServer();

server.requestHandler(function(req) {
  tu.checkContext();
  req.response.end('Hello World');
}).listen(8080, 'localhost');

tu.appReady();

function vertxStop() {
  tu.checkContext();
  server.close(function() {
    tu.checkContext();
    tu.appStopped();
  });
}
