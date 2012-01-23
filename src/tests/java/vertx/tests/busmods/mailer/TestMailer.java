package vertx.tests.busmods.mailer;

import org.vertx.java.busmods.mailer.Mailer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.newtests.TestUtils;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestMailer extends Mailer {

  private TestUtils tu = new TestUtils();

  public TestMailer() {
    super("testMailer", "localhost", 25);
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
  public void handle(Message message) {
    tu.azzert(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));
    super.handle(message);
  }
}
