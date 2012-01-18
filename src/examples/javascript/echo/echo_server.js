load('core/vertx.js')

var server = new vertx.NetServer();

server.connectHandler(function(sock) {
//  sock.dataHandler(function(data) {
//    sock.write(data);
//  })
  new vertx.Pump(sock, sock).start();
})

server.listen(1234, 'localhost');

function vertxStop() {
  server.close
}
