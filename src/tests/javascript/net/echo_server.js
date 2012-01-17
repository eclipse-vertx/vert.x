//load('core/net.js')
load('test_utils.js')

var tu = new TestUtils();

var server = new vertx.NetServer();

server.connectHandler(function(sock) {
  tu.checkContext();
  sock.dataHandler(function(data) {
    tu.checkContext();
    sock.write(data);
  })
})

server.listen(8080, 'localhost');

tu.appReady();

function vertxStop() {
  tu.checkContext();
  server.close(function() {
    tu.checkContext();
    tu.appStopped();
  });
}
