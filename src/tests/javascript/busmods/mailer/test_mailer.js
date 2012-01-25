load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var mailer = new vertx.Mailer("test.mailer", "localhost");
mailer.start();
tu.appReady();

function vertxStop() {
  mailer.stop();
  tu.checkContext();
  tu.appStopped();
}
