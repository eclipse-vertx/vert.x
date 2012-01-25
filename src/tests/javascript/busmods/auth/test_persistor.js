load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var persistor = new vertx.Persistor("test.persistor");
persistor.start();
tu.appReady();

function vertxStop() {
  persistor.stop();
  tu.checkContext();
  tu.appStopped();
}
