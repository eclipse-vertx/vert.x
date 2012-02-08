// This is just a wrapper around the Java work queue

var j_q = new org.vertx.java.busmods.workqueue.WorkQueue();

j_q.start();

function vertxStop() {
  j_q.stop();
}
