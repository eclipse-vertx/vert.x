load('vertx.js')

var client = vertx.createNetClient();

client.connect(1234, function(sock) {
  stdout.println("connected");

  sock.dataHandler(function(buffer) {
    stdout.println("client receiving " + buffer.toString());
  });

  // Now send some data
  for (var i = 0; i < 10; i++) {
    var str = "hello" + i + "\n";
    stdout.println("Net client sending: " + str);
    sock.write(new vertx.Buffer(str));
  }
});


