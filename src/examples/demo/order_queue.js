load('vertx.js');

var queue = new vertx.WorkQueue("demo.orderQueue", 30000);
queue.start();

function vertxStop() {
  queue.stop();
  tu.appStopped();
}
