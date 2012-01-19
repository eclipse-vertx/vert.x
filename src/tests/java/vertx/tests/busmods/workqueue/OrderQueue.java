package vertx.tests.busmods.workqueue;

import org.vertx.java.busmods.workqueue.WorkQueue;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OrderQueue extends WorkQueue {

  private TestUtils tu = new TestUtils();

  public OrderQueue() {
    super("orderQueue", 30000);
  }

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
    tu.appStopped();
  }
}
