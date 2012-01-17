package vertx.tests.blockingaction;

import org.vertx.java.core.BlockingAction;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.newtests.TestClientBase;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
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

  public void testBlockingAction() {

    final int numActions = 100;

    class AggHandler {
      int count;
      void complete() {
        if (++count == 2 * numActions) {
          tu.testComplete();
        }
      }
    }

    final AggHandler agg = new AggHandler();

    for (int i = 0; i < numActions; i++) {
      // One that succeeeds
      new BlockingAction<String>() {
        protected String action() throws Exception {
          return "foo";
        }
      }.handler(new CompletionHandler<String>() {
        public void handle(Future<String> event) {
          tu.azzert(event.succeeded());
          tu.azzert("foo".equals(event.result()));
          agg.complete();
        }
      }).execute();

      // One that throws an exception
      new BlockingAction<String>() {
        protected String action() throws Exception {
          throw new Exception("Wibble");
        }
      }.handler(new CompletionHandler<String>() {
        public void handle(Future<String> event) {
          tu.azzert(!event.succeeded());
          tu.azzert("Wibble".equals(event.exception().getMessage()));
          agg.complete();
        }
      }).execute();
    }
  }


}
