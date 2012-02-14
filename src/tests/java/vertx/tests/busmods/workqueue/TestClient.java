package vertx.tests.busmods.workqueue;

import org.vertx.java.busmods.workqueue.WorkQueue;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.newtests.TestClientBase;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private EventBus eb = EventBus.instance;

  private String queueID;

  @Override
  public void start() {
    super.start();
    JsonObject config = new JsonObject();
    config.putString("address", "test.orderQueue");
    queueID = Vertx.instance.deployWorkerVerticle(WorkQueue.class.getName(), config, 1, new SimpleHandler() {
      public void handle() {
        tu.appReady();
      }
    });
  }

  @Override
  public void stop() {
    super.stop();
//     Vertx.instance.undeployVerticle(queueID, new SimpleHandler() {
//       public void handle() {
//         TestClient.super.stop();
//       }
//     });
  }

  int count;

  public void test1() throws Exception {

    final int numMessages = 30;

    eb.registerHandler("done", new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        if (++count == numMessages) {
          eb.unregisterHandler("done", this);
          tu.testComplete();
        }
      }
    });

    for (int i = 0; i < numMessages; i++) {
      JsonObject obj = new JsonObject().putString("blah", "wibble" + i);
      eb.send("test.orderQueue", obj);
    }
  }

}
