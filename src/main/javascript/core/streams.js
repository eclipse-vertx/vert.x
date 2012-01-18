var vertx = vertx || {};

vertx.Pump = function(rs, ws) {
  return new org.vertx.java.core.streams.Pump(rs, ws);
}
