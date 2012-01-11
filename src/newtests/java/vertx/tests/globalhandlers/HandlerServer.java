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
public class HandlerServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  protected ContextChecker check;

  private long id;

  public void start() {
    check = new ContextChecker(tu);
    id = Vertx.instance.registerHandler(new Handler<String>() {
      public void handle(String message) {
        check.check();
        tu.testComplete();
      }
    });
    SharedData.getSet("handlerids").add(id);
    tu.appReady();
  }

  public void stop() {
    Vertx.instance.unregisterHandler(id);
    tu.appStopped();
  }


}
