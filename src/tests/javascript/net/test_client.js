load('test_utils.js')
load('core/net.js')

var tu = new TestUtils();

var client;

tu.register('test1', function() {

  client = new vertx.NetClient();

  client.connect(8080, 'localhost', function(sock) {

    sock.dataHandler(function(data) {
      //log.println("Got data echoed back");
      tu.testComplete();
    });

    sock.write(new vertx.Buffer('this is a buffer'));

  });

})

tu.appReady();

function vertxStop() {
  client.close();
  tu.appStopped();
}