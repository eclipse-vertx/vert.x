var vertx = vertx || {};

if (!vertx.getLogger) {

  vertx.getLogger = function() {
    return org.vertx.java.core.Vertx.instance.getLogger();
  }
}
