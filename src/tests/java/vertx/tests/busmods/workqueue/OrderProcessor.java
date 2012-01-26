package vertx.tests.busmods.workqueue;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.newtests.TestUtils;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OrderProcessor implements VertxApp, Handler<JsonMessage> {

  private TestUtils tu = new TestUtils();

  private EventBus eb = EventBus.instance;

  private String address = UUID.randomUUID().toString();

  @Override
  public void start() throws Exception {
    eb.registerJsonHandler(address, this);
    JsonObject msg = new JsonObject();
    msg.putString("processor", address);
    eb.sendJson("orderQueue.register", msg);
    tu.appReady();
  }


  @Override
  public void stop() throws Exception {

    JsonObject msg = new JsonObject();
    msg.putString("processor", address);
    eb.sendJson("orderQueue.unregister", msg);

    eb.unregisterJsonHandler(address, this);

    tu.appStopped();
  }

  public void handle(final JsonMessage message) {
    try {
      // Simulate some processing time - ok to sleep here since this is a worker application
      Thread.sleep(100);

      message.reply();
      eb.sendJson("done", new JsonObject());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
