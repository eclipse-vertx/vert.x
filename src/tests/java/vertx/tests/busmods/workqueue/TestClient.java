package vertx.tests.busmods.workqueue;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.newtests.TestClientBase;

import java.util.HashMap;
import java.util.Map;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private EventBus eb = EventBus.instance;
  private JsonHelper sender = new JsonHelper();

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  int count;

  public void test1() throws Exception {

    final int numMessages = 30;

    eb.registerHandler("done", new Handler<Message>() {
      public void handle(Message message) {
        if (++count == numMessages) {
          eb.unregisterHandler("done", this);
          tu.testComplete();
        }
      }
    });

    for (int i = 0; i < numMessages; i++) {

      Map<String, Object> map = new HashMap<>();
      map.put("address", "orderQueue");
      map.put("action", "send");
      map.put("blah", "wibble" + i);
      sender.sendJSON(map);
    }
  }

}
