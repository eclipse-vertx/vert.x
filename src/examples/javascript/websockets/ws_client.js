load('vertx.js')

var log = vertx.getLogger();

var client = new vertx.HttpClient().setPort(8181);

client.connectWebsocket('/some-uri', function(websocket) {

  websocket.dataHandler(function(data) {
    log.info('Received data ' + data);
  });

  websocket.writeTextFrame('Hello world');

  client.close();

});
