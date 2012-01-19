package vertx.tests.core.globalhandlers;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Immutable;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestClientBase;

import java.math.BigDecimal;
import java.util.HashMap;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testHandlers() {
    for (long id: SharedData.<Long>getSet("handlerids")) {
      String msg = "hello";
      Vertx.instance.sendToHandler(id, msg);
    }
  }

  public void testUnregisterFromDifferentContext() {
    for (long id: SharedData.<Long>getSet("handlerids")) {
      try {
        Vertx.instance.unregisterHandler(id);
        tu.azzert(false, "Should throw exception");
      } catch (IllegalStateException e) {
        //OK
      }
    }
    tu.testComplete();
  }

  public void testUnregisteredHandlers() {
    for (long id: SharedData.<Long>getSet("handlerids")) {
      String msg = "hello";
      Vertx.instance.sendToHandler(id, msg);
    }
    // Wait a short while to enable messages to be routed
    Vertx.instance.setTimer(100, new Handler<Long>() {
      public void handle(Long id) {
        tu.testComplete();
      }
    });
  }

  public void testSendDataTypes() {

    // These are the data types you can send:
    Vertx.instance.sendToHandler(1, "hello");
    Vertx.instance.sendToHandler(1, 123);
    Vertx.instance.sendToHandler(1, 123d);
    Vertx.instance.sendToHandler(1, 123f);
    Vertx.instance.sendToHandler(1, 123l);
    Vertx.instance.sendToHandler(1, (short)123);
    Vertx.instance.sendToHandler(1, (byte)12);
    Vertx.instance.sendToHandler(1, 'x');
    Vertx.instance.sendToHandler(1, true);
    Vertx.instance.sendToHandler(1, new BigDecimal(123));
    Vertx.instance.sendToHandler(1, new byte[] { 12, 13, 14});
    Vertx.instance.sendToHandler(1, Buffer.create("hello"));
    Vertx.instance.sendToHandler(1, new Immutable(){});

    // Anything else you can't

    try {
      Vertx.instance.sendToHandler(1, new HashMap());
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      Vertx.instance.sendToHandler(1, new Object());
      tu.azzert(false, "Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    tu.testComplete();

  }

}
