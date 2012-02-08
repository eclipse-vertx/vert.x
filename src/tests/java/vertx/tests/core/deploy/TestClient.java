package vertx.tests.core.deploy;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
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

  public void testDeploy() {
    eb.registerHandler("test-handler", new Handler<Message<String>>() {
      public void handle(Message<String> message) {
        tu.azzert("started".equals(message.body));
        tu.testComplete();
      }
    });

    Vertx.instance.deployVerticle("vertx.tests.core.deploy.ChildVerticle");
  }

  public void testUndeploy() {

    final String id = Vertx.instance.deployVerticle("vertx.tests.core.deploy.ChildVerticle");

    Vertx.instance.setTimer(100, new Handler<Long>() {
      public void handle(Long tid) {
        eb.registerHandler("test-handler", new Handler<Message<String>>() {
          public void handle(Message<String> message) {
            tu.azzert("stopped".equals(message.body));
            tu.testComplete();
          }
        });
        Vertx.instance.undeployVerticle(id);
      }
    });

  }
}

