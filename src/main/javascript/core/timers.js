var vertx = vertx || {};

vertx.setTimer = function(delay, handler) {
  org.vertx.java.core.Vertx.instance.setTimer(delay, handler);
}

vertx.setPeriodic = function(interval, handler) {
  org.vertx.java.core.Vertx.instance.setPeriodic(interval, handler);
}

vertx.cancelTimer = function(id) {
  org.vertx.java.core.Vertx.instance.cancelTimer(id);
}