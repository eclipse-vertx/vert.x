import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.testframework.TestClientBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class App extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.testComplete();
  }

}