load('vertx.js')

var client = new vertx.HttpClient().setPort(8181);

client.connectWebsocket('/some-uri', function(websocket) {

  log.println('ws connected');

  websocket.dataHandler(function(data) {
    log.println('Received data ' + data);
  });

  websocket.writeTextFrame('Hello world');

  client.close();

});
