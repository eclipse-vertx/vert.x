package vertx.tests.core.globalhandlers;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private long id;

  public void start() {
//    id = Vertx.instance.registerHandler(new Handler<String>() {
//      public void handle(String message) {
//        tu.checkContext();
//        tu.testComplete();
//      }
//    });
//    SharedData.getSet("handlerids").add(id);
//    tu.appReady();
  }

  public void stop() {
   // Vertx.instance.unregisterHandler(id);
    tu.appStopped();
  }


}
