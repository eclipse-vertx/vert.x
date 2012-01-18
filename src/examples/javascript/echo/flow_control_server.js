load('core/vertx.js')

var server = new vertx.NetServer();

server.connectHandler(function(sock) {
  sock.dataHandler(function(data) {
    sock.write(data);
    if (sock.writeQueueFull()) {
      sock.pause();
      sock.drainHandler(function() {
        sock.resume();
      })
    }
  })
})

server.listen(1234, 'localhost');

function vertxStop() {
  server.close
}
