load('vertx.js');

var authMgr = new vertx.AuthManager("demo.authMgr", "users", "demo.persistor");
authMgr.start();

function vertxStop() {
  authMgr.stop();
}
