package vertx.tests.globalhandlers;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class UnregisteringServer implements VertxApp {

  protected TestUtils tu = new TestUtils();
  protected ContextChecker check;

  public void start() {
    check = new ContextChecker(tu);

    long id = Vertx.instance.registerHandler(new Handler<String>() {
      public void handle(String message) {
        check.check();
        tu.azzert(false, "Should not receive message");
      }
    });

    SharedData.getSet("handlerids").add(id);

    //And immediately unregister it
    Vertx.instance.unregisterHandler(id);

    tu.appReady();
  }

  public void stop() {
    tu.appStopped();
  }
}
