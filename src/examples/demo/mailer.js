load('vertx.js');

var mailer = new vertx.Mailer("demo.mailer", "localhost");
mailer.start();

function vertxStop() {
  mailer.stop();
}
