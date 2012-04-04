load('vertx.js')

var client = vertx.createHttpClient().setPort(8080);

client.connectWebsocket('/some-uri', function(websocket) {
  websocket.dataHandler(function(data) {
    stdout.println('Received data ' + data);
    client.close();
  });
  websocket.writeTextFrame('Hello world');
});
