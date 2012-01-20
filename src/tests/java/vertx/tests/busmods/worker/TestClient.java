package vertx.tests.busmods.worker;

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
  private JsonHelper helper = new JsonHelper();

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

    Map<String, Object> map = new HashMap<>();
    map.put("address", "testWorker");
    map.put("foo", "wibble");
    helper.sendJSON(map, new Handler<Message>() {
      public void handle(Message message) {
        Map<String, Object> json = helper.toJson(message);
        tu.azzert(json.get("eek").equals("blurt"));
        tu.testComplete();
      }
    });
  }

}
