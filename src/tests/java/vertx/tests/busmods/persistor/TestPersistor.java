package vertx.tests.busmods.persistor;

import org.vertx.java.busmods.persistor.Persistor;
import org.vertx.java.newtests.TestUtils;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestPersistor extends Persistor {

  private TestUtils tu = new TestUtils();

  public TestPersistor() {
    super("testPersistor");
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

}
