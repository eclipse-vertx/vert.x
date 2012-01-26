package vertx.tests.busmods.worker;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonMessage;
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
    eb.sendJson("testWorker", obj, new Handler<JsonMessage>() {
      public void handle(JsonMessage message) {
        tu.azzert(message.jsonObject.getString("eek").equals("blurt"));
        tu.testComplete();
      }
    });
  }

}
