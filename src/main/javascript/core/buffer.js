var vertx = vertx || {};

vertx.Buffer = function(p) {
  return org.vertx.java.core.buffer.Buffer.create(p);
}
