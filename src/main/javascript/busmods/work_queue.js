var vertx = vertx || {};

vertx.WorkQueue = function(address, timeout, persistorAddress, collection) {
  if (typeof persistorAddress === 'undefined') {
    return new org.vertx.java.busmods.workqueue.WorkQueue(address, timeout);
  } else {
    return new org.vertx.java.busmods.workqueue.WorkQueue(address, timeout, persistorAddress, collection);
  }
}
