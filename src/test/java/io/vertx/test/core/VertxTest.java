package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxTest extends AsyncTestBase {

  @Test
  public void testCloseHooksCalled() throws Exception {
    AtomicInteger closedCount = new AtomicInteger();
    Closeable myCloseable1 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.handle(Future.succeededFuture());
    };
    Closeable myCloseable2 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.handle(Future.succeededFuture());
    };
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    vertx.addCloseHook(myCloseable1);
    vertx.addCloseHook(myCloseable2);
    // Now undeploy
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      assertEquals(2, closedCount.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testCloseHookFailure1() throws Exception {
    AtomicInteger closedCount = new AtomicInteger();
    class Hook implements Closeable {
      @Override
      public void close(Handler<AsyncResult<Void>> completionHandler) {
        if (closedCount.incrementAndGet() == 1) {
          throw new RuntimeException();
        } else {
          completionHandler.handle(Future.succeededFuture());
        }
      }
    }
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    vertx.addCloseHook(new Hook());
    vertx.addCloseHook(new Hook());
    // Now undeploy
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      assertEquals(2, closedCount.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testCloseHookFailure2() throws Exception {
    AtomicInteger closedCount = new AtomicInteger();
    class Hook implements Closeable {
      @Override
      public void close(Handler<AsyncResult<Void>> completionHandler) {
        if (closedCount.incrementAndGet() == 1) {
          completionHandler.handle(Future.succeededFuture());
          throw new RuntimeException();
        } else {
          completionHandler.handle(Future.succeededFuture());
        }
      }
    }
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    vertx.addCloseHook(new Hook());
    vertx.addCloseHook(new Hook());
    // Now undeploy
    vertx.close(ar -> {
      assertTrue(ar.succeeded());
      assertEquals(2, closedCount.get());
      testComplete();
    });
    await();
  }
}
