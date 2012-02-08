var vertx = vertx || {};

if (!vertx.Pump) {
  vertx.Pump = function(rs, ws) {
    return new org.vertx.java.core.streams.Pump(rs, ws);
  }
}
