load('vertx.js');

var mailer = new vertx.Mailer("demo.mailer", "localhost");

// To use a gmail account, uncomment the following and substitute your username and password
// var mailer = new vertx.Mailer('demo.mailer', 'smtp.googlemail.com', 465, true, true, 'username', 'password');

mailer.start();

function vertxStop() {
  mailer.stop();
}
