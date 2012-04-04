load('vertx.js')

vertx.createNetServer().connectHandler(function(sock) {
  new vertx.Pump(sock, sock).start();
}).listen(1234, 'localhost');
