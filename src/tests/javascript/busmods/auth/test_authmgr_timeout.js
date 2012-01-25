load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var authMgr = new vertx.AuthManager("test.authMgr", "users", "test.persistor", 500);
authMgr.start();
tu.appReady();

function vertxStop() {
  authMgr.stop();
  tu.checkContext();
  tu.appStopped();
}
