package vertx.tests.workqueue;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.newtests.TestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OrderProcessor implements VertxApp, Handler<Message> {

  private TestUtils tu = new TestUtils();

  private EventBus eb = EventBus.instance;

  private String address = UUID.randomUUID().toString();

  private ObjectMapper mapper = new ObjectMapper();

  private JsonHelper helper = new JsonHelper();

  private int count;

  @Override
  public void start() throws Exception {
    eb.registerHandler(address, this);

    Map<String, Object> msg = new HashMap<>();
    msg.put("address", "orderQueue");
    msg.put("action", "register");
    msg.put("processor", address);
    helper.sendJSON(msg);

    tu.appReady();
  }


  @Override
  public void stop() throws Exception {

    Map<String, Object> msg = new HashMap<>();
    msg.put("address", "orderQueue");
    msg.put("action", "unregister");
    msg.put("processor", address);
    helper.sendJSON(msg);

    eb.unregisterHandler(address, this);

    tu.appStopped();
  }

  public void handle(final Message message) {
    try {
      Map<String, Object> json = helper.toJson(message);
      Vertx.instance.setTimer(100, new Handler<Long>() {
        public void handle(Long id) {
          message.reply();
          eb.send(new Message("done"));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
