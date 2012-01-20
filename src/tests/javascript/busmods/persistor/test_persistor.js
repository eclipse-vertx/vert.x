load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var persistor = new vertx.Persistor("testPersistor");
persistor.start();
tu.appReady();

log.println("Started persistor");

function vertxStop() {
  persistor.stop();
  tu.checkContext();
  tu.appStopped();
}
