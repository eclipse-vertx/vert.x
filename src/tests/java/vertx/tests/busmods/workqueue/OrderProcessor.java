package vertx.tests.busmods.workqueue;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.newtests.TestUtils;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OrderProcessor implements Verticle, Handler<Message<JsonObject>> {

  private TestUtils tu = new TestUtils();

  private EventBus eb = EventBus.instance;

  private String address = UUID.randomUUID().toString();

  @Override
  public void start() throws Exception {
    eb.registerHandler(address, this);
    JsonObject msg = new JsonObject().putString("processor", address);
    eb.send("test.orderQueue.register", msg);
    tu.appReady();
  }


  @Override
  public void stop() throws Exception {

    JsonObject msg = new JsonObject().putString("processor", address);
    eb.send("test.orderQueue.unregister", msg);

    eb.unregisterHandler(address, this);

    tu.appStopped();
  }

  public void handle(Message<JsonObject> message) {
    try {
      // Simulate some processing time - ok to sleep here since this is a worker application
      Thread.sleep(100);

      message.reply();
      eb.send("done", new JsonObject());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
