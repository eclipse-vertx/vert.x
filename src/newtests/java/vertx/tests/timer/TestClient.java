package vertx.tests.timer;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestClientBase;

import java.util.concurrent.atomic.AtomicLong;

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

  public void testOneOff() throws Exception {
    final ContextChecker check = new ContextChecker(tu);
    final AtomicLong id = new AtomicLong(-1);
    id.set(Vertx.instance.setTimer(1, new Handler<Long>() {
      int count;
      public void handle(Long timerID) {
        check.check();
        tu.azzert(id.get() == timerID.longValue());
        tu.azzert(count == 0);
        count++;
        setEndTimer(check);
      }
    }));
  }

  private void setEndTimer(final ContextChecker check) {
    // Set another timer to trigger test complete - this is so if the first timer is called more than once we will
    // catch it
    Vertx.instance.setTimer(10, new Handler<Long>() {
      public void handle(Long timerID) {
        check.check();
        tu.testComplete();
      }
    });
  }

  public void testPeriodic() throws Exception {
    final int numFires = 10;
    final long delay = 100;
    final ContextChecker check = new ContextChecker(tu);
    final AtomicLong id = new AtomicLong(-1);
    id.set(Vertx.instance.setPeriodic(delay, new Handler<Long>() {
      int count;
      public void handle(Long timerID) {
        check.check();
        tu.azzert(id.get() == timerID.longValue());
        count++;
        if (count == numFires) {
          Vertx.instance.cancelTimer(timerID);
          setEndTimer(check);
        }
        if (count > numFires) {
          tu.azzert(false, "Fired too many times");
        }
      }
    }));
  }

  /*
  Test the timers fire with approximately the correct delay
   */
  public void testTimings() throws Exception {
    final ContextChecker check = new ContextChecker(tu);
    final long start = System.nanoTime();
    final long delay = 500;
    Vertx.instance.setTimer(delay, new Handler<Long>() {
      public void handle(Long timerID) {
        check.check();
        long dur = (System.nanoTime() - start) / 1000000;
        tu.azzert(dur >= delay);
        tu.azzert(dur < delay * 1.5); // 50% margin of error
        Vertx.instance.cancelTimer(timerID);
        tu.testComplete();
      }
    });
  }


}
