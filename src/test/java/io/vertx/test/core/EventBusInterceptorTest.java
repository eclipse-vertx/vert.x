package io.vertx.test.core;

import io.vertx.core.eventbus.EventBus;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusInterceptorTest extends VertxTestBase {

  EventBus eb;

  @Test
  public void testInterceptor() {
    eb.addInterceptor(sc -> {
      assertEquals("armadillo", sc.message().body());
      sc.next();
    });
    eb.consumer("some-address", msg -> {
      assertEquals("armadillo", msg.body());
      testComplete();
    });
    eb.send("some-address", "armadillo");
    await();
  }

  @Test
  public void testInterceptorNoNext() {
    eb.addInterceptor(sc -> {
      assertEquals("armadillo", sc.message().body());
    });
    eb.consumer("some-address", msg -> {
      fail("Should not receive message");
    });
    eb.send("some-address", "armadillo");
    vertx.setTimer(200, tid -> testComplete());
    await();
  }

  @Test
  public void testMultipleInterceptors() {
    AtomicInteger cnt = new AtomicInteger();
    int interceptorNum = 10;
    for (int i = 0; i < interceptorNum; i++) {
      final int expectedCount = i;
      eb.addInterceptor(sc -> {
        assertEquals("armadillo", sc.message().body());
        int count = cnt.getAndIncrement();
        assertEquals(expectedCount, count);
        sc.next();
      });
    }
    eb.consumer("some-address", msg -> {
      assertEquals("armadillo", msg.body());
      assertEquals(interceptorNum, cnt.get());
      testComplete();
    });
    eb.send("some-address", "armadillo");
    await();
  }

  /*
  TODO tests for:
  * remove interceptors
  * interceptors on reply
   */

  @Override
  public void setUp() throws Exception {
    super.setUp();

    eb = vertx.eventBus();
  }
}
