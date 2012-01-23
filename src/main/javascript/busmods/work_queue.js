var vertx = vertx || {};

vertx.WorkQueue = function(address, timeout, persistorAddress, collection) {
  if (persistorAddress && collection) {
    return new org.vertx.java.busmods.workqueue.WorkQueue(address, timeout, persistorAddress, collection);
  } else {
    return new org.vertx.java.busmods.workqueue.WorkQueue(address, timeout);
  }
}
