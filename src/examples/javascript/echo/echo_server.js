load('core/net.js')

var server = new vertx.NetServer();

server.connectHandler(function(sock) {
  sock.dataHandler(function(data) {
    sock.write(data);
  })
})

server.listen(1234, 'localhost');

function vertxStop() {
  server.close
}
