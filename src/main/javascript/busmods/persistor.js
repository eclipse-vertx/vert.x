var vertx = vertx || {};

vertx.Persistor = function(address, dbName, host, port) {
  if (typeof host === 'undefined') {
    return new org.vertx.java.busmods.persistor.Persistor(address, dbName);
  } else {
    return new org.vertx.java.busmods.persistor.Persistor(address, dbName, host, port);
  }
}
