var vertx = vertx || {};

vertx.AuthManager = function(address, userCollection, persistorAddress, sessionTimeout) {
  if (typeof sessionTimeout != 'undefined') {
    return new org.vertx.java.busmods.auth.AuthManager(address, userCollection, persistorAddress, sessionTimeout);
  } else {
    return new org.vertx.java.busmods.auth.AuthManager(address, userCollection, persistorAddress);
  }
}

