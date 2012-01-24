var vertx = vertx || {};

vertx.Mailer = function(address, host, port, ssl, auth, username, password) {

  if (typeof ssl === 'undefined') {
    if (typeof port == 'undefined') {
      return new org.vertx.java.busmods.mailer.Mailer(address, host);
    } else {
      return new org.vertx.java.busmods.mailer.Mailer(address, host, port);
    }
  } else {
    return new org.vertx.java.busmods.mailer.Mailer(address, host, port, ssl, auth, username, password);
  }
}
