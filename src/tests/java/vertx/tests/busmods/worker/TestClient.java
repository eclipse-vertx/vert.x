package vertx.tests.busmods.worker;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.newtests.TestClientBase;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private EventBus eb = EventBus.instance;

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testWorker() throws Exception {
    JsonObject obj = new JsonObject().putString("foo", "wibble");
    eb.send("testWorker", obj, new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        tu.azzert(message.body.getString("eek").equals("blurt"));
        tu.testComplete();
      }
    });
  }

}
