package vertx.tests.busmods.mailer;

import org.vertx.java.busmods.mailer.Mailer;
import org.vertx.java.core.eventbus.JsonMessage;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestMailer extends Mailer {

  private TestUtils tu = new TestUtils();

  public TestMailer() {
    super("test.mailer", "localhost", 25);
  }

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
    tu.appStopped();
  }

  @Override
  public void handle(Message<JsonObject> message) {
    tu.azzert(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));
    super.handle(message);
  }
}
