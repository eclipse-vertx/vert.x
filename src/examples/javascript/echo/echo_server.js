load('core/net.js')

var server = new vertx.NetServer();

server.connectHandler(function(sock) {
  log.println("Connected: " + sock);
  sock.dataHandler(function(data) {
    log.println("Echoing data " + data);
    sock.write(data);
  })
})

server.listen(8080, 'localhost');

function vertxStop() {
  log.println("In vertxStop");
}
