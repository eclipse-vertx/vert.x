load('vertx.js');

var queue = new vertx.WorkQueue("demo.orderQueue", 30000, "demo.persistor", "orders");
queue.start();

function vertxStop() {
  queue.stop();
  tu.appStopped();
}
