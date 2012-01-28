package vertx.tests.busmods.worker;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestWorker implements VertxApp, Handler<Message<JsonObject>> {

  private TestUtils tu = new TestUtils();

  private EventBus eb = EventBus.instance;

  private String address = "testWorker";

  @Override
  public void start() throws Exception {
    eb.registerHandler(address, this);
    tu.appReady();
  }


  @Override
  public void stop() throws Exception {
    eb.unregisterHandler(address, this);
    tu.appStopped();
  }

  public void handle(final Message<JsonObject> message) {
    try {
      tu.azzert(message.body.getString("foo").equals("wibble"));
      tu.azzert(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));

      // Trying to create any network clients or servers should fail - workers can only use the event bus

      try {
        new NetServer();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

       try {
        new NetClient();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

       try {
        new HttpServer();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

       try {
        new HttpClient();
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }

      // Simulate some processing time - ok to sleep here since this is a worker application
      Thread.sleep(100);

      JsonObject reply = new JsonObject().putString("eek", "blurt");
      message.reply(reply);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
