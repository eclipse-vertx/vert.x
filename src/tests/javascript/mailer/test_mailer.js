load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var mailer = new vertx.Mailer("testMailer", "localhost");
mailer.start();
tu.appReady();

log.println("Started mailer");

function vertxStop() {
  mailer.stop();
  tu.checkContext();
  tu.appStopped();
}
