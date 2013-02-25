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