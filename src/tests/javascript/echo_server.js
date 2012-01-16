load('core/net.js')
load('test_utils.js')

var tu = new TestUtils();

var server = new vertx.NetServer();

server.connectHandler(function(sock) {
  sock.dataHandler(function(data) {
    sock.write(data);
  })
})

server.listen(8080, 'localhost');

tu.appReady();

function vertxStop() {
  tu.appStopped();
}
