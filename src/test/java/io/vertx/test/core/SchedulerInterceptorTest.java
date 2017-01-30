package io.vertx.test.core;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Unit-Tests of {@link Vertx} scheduler interceptor behavior
 */
public class SchedulerInterceptorTest extends VertxTestBase {

  @Test
  public void testRunOnContext() {
    AtomicInteger cnt = new AtomicInteger();
    vertx.addSchedulerInterceptor((c, r) -> {
      cnt.incrementAndGet();
      return r;
    });

    vertx.runOnContext(v -> {
      assertEquals(1, cnt.get());
    });
    vertx.runOnContext(v -> {
      assertEquals(2, cnt.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testRunOnContext_WithMultipleInterceptors() {
    Context ctx = vertx.getOrCreateContext();

    int interceptorNum = 10;
    AtomicInteger cnt = new AtomicInteger();

    for (int i = 0; i < interceptorNum; i++) {
      final int expectedCount = i;
      vertx.addSchedulerInterceptor((c, r) -> {
        if (c == ctx) {
          assertEquals(expectedCount, cnt.getAndIncrement());
        }
        return r;
      });
    }

    ctx.runOnContext(v -> {
      assertEquals(interceptorNum, cnt.get());
      testComplete();
    });

    await();
  }

  @Test
  public void testExecuteBlocking() {
    Context ctx = vertx.getOrCreateContext();
    AtomicInteger cnt = new AtomicInteger();

    vertx.addSchedulerInterceptor((c, r) -> {
      if (c == ctx) {
        cnt.incrementAndGet();
      }
      return r;
    });

    ctx.executeBlocking(f -> {
      assertEquals(1, cnt.get());
    }, true, null);

    ctx.executeBlocking(f -> {
      assertEquals(2, cnt.get());
      testComplete();
    }, true, null);

    await();
  }

  @Test
  public void testTimer() {
    Context ctx = vertx.getOrCreateContext();
    AtomicInteger cnt = new AtomicInteger();

    vertx.addSchedulerInterceptor((c, r) -> {
      if (c == ctx) {
        cnt.incrementAndGet();
      }
      return r;
    });

    ctx.runOnContext(v -> {
      assertEquals(1, cnt.get());
      vertx.setTimer(1000, h -> {
        assertEquals(2, cnt.get());
        vertx.runOnContext(v2 -> {
          assertEquals(3, cnt.get());
          testComplete();
        });
      });
    });

    await();
  }

  @Test
  public void testRunPeriodic() {
    Context ctx = vertx.getOrCreateContext();
    AtomicInteger cnt = new AtomicInteger();

    vertx.addSchedulerInterceptor((c, r) -> {
      if (c == ctx) {
        cnt.incrementAndGet();
      }
      return r;
    });

    ctx.runOnContext(v -> {
      assertEquals(1, cnt.get());
      vertx.setPeriodic(1000, h -> {
        assertEquals(2, cnt.get());
        vertx.cancelTimer(h);
        vertx.runOnContext(v2 -> {
          assertEquals(3, cnt.get());
          testComplete();
        });
      });
    });

    await();
  }

  @Test
  public void testRemoveInterceptor() {
    AtomicInteger cnt1 = new AtomicInteger();
    AtomicInteger cnt2 = new AtomicInteger();

    BiFunction<Context, Runnable, Runnable> si1 = (c, r) -> {
      cnt1.incrementAndGet();
      return r;
    };

    BiFunction<Context, Runnable, Runnable> si2 = (c, r) -> {
      cnt2.incrementAndGet();
      return r;
    };

    vertx.addSchedulerInterceptor(si1);
    vertx.addSchedulerInterceptor(si2);

    vertx.runOnContext(v -> {
      assertEquals(1, cnt1.get());
      assertEquals(1, cnt2.get());
      vertx.removeSchedulerInterceptor(si2);
    });
    vertx.runOnContext(v -> {
      assertEquals(2, cnt1.get());
      assertEquals(1, cnt2.get());
      testComplete();
    });

    await();
  }
}
