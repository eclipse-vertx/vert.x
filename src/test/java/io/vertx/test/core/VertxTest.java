package io.vertx.test.core;

import io.vertx.core.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.test.fakecluster.FakeClusterManager;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
