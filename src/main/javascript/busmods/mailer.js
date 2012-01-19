var vertx = vertx || {};

vertx.Mailer = function(address, host, port, ssl, auth, username, password) {
  if (address && host) {
    if (port) {
      if (ssl && auth && username && password) {
        return new org.vertx.java.busmods.mailer.Mailer(address, host, port, ssl, auth, username, password);
      } else {
        return new org.vertx.java.busmods.mailer.Mailer(address, host, port);
      }
    } else {
      return new org.vertx.java.busmods.mailer.Mailer(address, host);
    }
  } else {
    throw "At minium address and port must be specified";
  }
}
