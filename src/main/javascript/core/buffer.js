var vertx = vertx || {};

if (!vertx.Buffer) {
  vertx.Buffer = function(p) {
    return org.vertx.java.core.buffer.Buffer.create(p);
  }
}
