var vertx = vertx || {};

vertx.Persistor = function(address) {
  return new org.vertx.java.busmods.persistor.Persistor(address);
}
