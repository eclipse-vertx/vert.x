var vertx = vertx || {};

vertx.WorkQueue = function(address, timeout) {
  return new org.vertx.java.busmods.workqueue.WorkQueue(address, timeout);
}
