load('vertx.js')

new vertx.NetServer().connectHandler(function(sock) {
  new vertx.Pump(sock, sock).start();
}).listen(1234, 'localhost');
