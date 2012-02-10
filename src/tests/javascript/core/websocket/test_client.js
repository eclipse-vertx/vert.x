load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var server = new vertx.HttpServer();
var client = new vertx.HttpClient();
client.setPort(8080);

function testEchoBinary() {
  echo(true);
}

function testEchoText() {
  echo(false);
}

function echo(binary) {

  server.websocketHandler(function(ws) {

    tu.checkContext();

    ws.dataHandler(function(buff) {
      tu.checkContext();
      ws.writeBuffer(buff);
    });

  });

  server.listen(8080);

  var buff;
  var str;
  if (binary) {
    buff = tu.generateRandomBuffer(1000);
  } else {
    str = tu.randomUnicodeString(1000);
    buff = new vertx.Buffer(str);
  }

  client.connectWebsocket("/someurl", function(ws) {
    tu.checkContext();

    var received = new vertx.Buffer(0);

    ws.dataHandler(function(buff) {
      tu.checkContext();
      received.appendBuffer(buff);
      if (received.length() == buff.length()) {
        tu.azzert(tu.buffersEqual(buff, received));
        tu.testComplete();
      }
    });

    if (binary) {
      ws.writeBinaryFrame(buff) ;
    } else {
      ws.writeTextFrame(str);
    }
  });

}

function testWriteFromConnectHandler() {

  server.websocketHandler(function(ws) {
    tu.checkContext();
    ws.writeTextFrame("foo");
  });

  server.listen(8080);

  client.connectWebsocket("/someurl", function(ws) {
    tu.checkContext();
    ws.dataHandler(function(buff) {
      tu.checkContext();
      tu.azzert("foo" == buff.toString());
      tu.testComplete();
    });
  });

}

function testClose() {

  server.websocketHandler(function(ws) {
    tu.checkContext();
    ws.dataHandler(function(buff) {
      ws.close();
    });
  });

  server.listen(8080);

  client.connectWebsocket("/someurl", function(ws) {
    tu.checkContext();
    ws.closedHandler(function() {
      tu.testComplete();
    });
    ws.writeTextFrame("foo");
  });

}

function testCloseFromConnectHandler() {

  server.websocketHandler(function(ws) {
    tu.checkContext();
    ws.close();
  });

  server.listen(8080);

  client.connectWebsocket("/someurl", function(ws) {
    tu.checkContext();
    ws.closedHandler(function() {
      tu.testComplete();
    });
  });

}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  client.close();
  server.close(function() {
    tu.unregisterAll();
    tu.appStopped();
  });
}

