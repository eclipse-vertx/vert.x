package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.test.core.VertxTestBase;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ZKAsyncMultiMapTest extends VertxTestBase {
  private CuratorFramework curator;
  private ZKAsyncMultiMap<String, String> asyncMultiMap;

  public void setUp() throws Exception {
    super.setUp();
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    curator = CuratorFrameworkFactory.builder().namespace("io.vertx").connectString("127.0.0.1").retryPolicy(retryPolicy).build();
    curator.start();

    final boolean[] startTest = {false};
    asyncMultiMap = new ZKAsyncMultiMap<>(vertx, curator, "multiMapTest");
    asyncMultiMap.add("myKey", "myValue", event -> {
      if (event.failed()) event.cause().printStackTrace();
      Assert.assertTrue(event.succeeded());
      startTest[0] = true;
    });
    waitUntil(() -> startTest[0]);
  }

  @Test
  public void asyncMultiMapGet() {
    asyncMultiMap.get("myKey", event -> {
      Assert.assertTrue(event.succeeded());
      event.result().forEach(s -> {
        if (s.equals("myValue")) {
          testComplete();
        }
      });
    });
    await();
  }

  @Test
  public void asyncMultiMapRemove() {
    asyncMultiMap.remove("myKey", "myValue", event -> {
      Assert.assertTrue(event.succeeded());
      Assert.assertTrue(event.result());
      asyncMultiMap.get("myKey", ea -> {
        Assert.assertTrue(ea.succeeded());
        ea.result().forEach(s -> {
          if (s.equals("myValue")) {
            Assert.fail("the value should be removed");
          }
        });
        testComplete();
      });
    });
    await();
  }

  @Test
  public void asyncMultiMapRemoveAll() {
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
    await();
  }


}
