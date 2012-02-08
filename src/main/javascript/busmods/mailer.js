// This is just a wrapper around the Java mailer

var j_mailer = new org.vertx.java.busmods.mailer.Mailer();

j_mailer.start();

function vertxStop() {
  j_mailer.stop();
}
