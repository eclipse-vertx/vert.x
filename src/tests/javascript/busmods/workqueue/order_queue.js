load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var queue = new vertx.WorkQueue("test.orderQueue", 30000);
queue.start();
tu.appReady();

function vertxStop() {
  queue.stop();
  tu.checkContext();
  tu.appStopped();
}
