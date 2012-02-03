load('vertx.js')

var server = new vertx.NetServer();

server.connectHandler(function(sock) {
  new vertx.Pump(sock, sock).start();
})

server.listen(1234, 'localhost');

function vertxStop() {
  server.close();
}
