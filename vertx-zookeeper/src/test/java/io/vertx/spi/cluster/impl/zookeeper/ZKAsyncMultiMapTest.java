package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.VertxSPI;
import io.vertx.test.core.VertxTestBase;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ZKAsyncMultiMapTest extends VertxTestBase {
  private ZKAsyncMultiMap<String, String> asyncMultiMap;

  @Test
  public void mockAsyncMultiMap() throws Exception {
    Timing timing = new Timing();
    TestingServer server = new TestingServer();

    RetryPolicy retryPolicy = new ExponentialBackoffRetry(100, 3);
    CuratorFramework curator = CuratorFrameworkFactory.builder().namespace("io.vertx").sessionTimeoutMs(10000).connectionTimeoutMs(timing.connection()).connectString(server.getConnectString()).retryPolicy(retryPolicy).build();
    curator.start();

    VertxSPI vertxSPI = (VertxInternal) vertx;
    asyncMultiMap = new ZKAsyncMultiMap<>(vertxSPI, curator, "multiMapTest");
    asyncMultiMap.add("myKey", "myValue", event -> {
      if (event.failed()) event.cause().printStackTrace();
      Assert.assertTrue(event.succeeded());
      asyncMultiMapRemove();
    });
    await();
  }

  private void asyncMultiMapRemove() {
    asyncMultiMap.get("myKey", ev -> {
      Assert.assertTrue(ev.succeeded());
      ev.result().forEach(s -> {
        assertEquals("myValue", s);
        //remove
        asyncMultiMap.remove("myKey", "myValue", eve -> {
          Assert.assertTrue(eve.succeeded());
          Assert.assertTrue(eve.result());
          asyncMultiMap.get("myKey", even -> {
            Assert.assertTrue(even.succeeded());
            even.result().forEach(el -> {
              if (el.equals("myValue")) {
                Assert.fail("the value should be removed");
              }
            });
            //remove all
            asyncMultiMapRemoveAll();
          });
        });
      });
    });
  }

  private void asyncMultiMapRemoveAll() {
    asyncMultiMap.add("myAnotherKey", "myValue", event -> {
      Assert.assertTrue(event.succeeded());
      asyncMultiMap.removeAllForValue("myValue", e -> {
        Assert.assertTrue(e.succeeded());
        asyncMultiMap.get("myAnotherKey", ea -> {
          Assert.assertTrue(ea.succeeded());
          ea.result().forEach(s -> {
            if (s.equals("myValue"))
              Assert.fail("the value should be removed");
          });
          testComplete();
        });
      });
    });
  }


}
