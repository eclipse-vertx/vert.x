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
public class ZKAsyncMapTest extends VertxTestBase {

  private CuratorFramework curator;
  private ZKAsyncMap<String, String> asyncMap;

  public void setUp() throws Exception {
    super.setUp();
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    curator = CuratorFrameworkFactory.builder().namespace("io.vertx").connectString("127.0.0.1").retryPolicy(retryPolicy).build();
    curator.start();



    final boolean[] startTest = {false};
    asyncMap = new ZKAsyncMap<>(vertx, curator, "mapTest");
    asyncMap.put("myKey", "myValue", event -> {
      Assert.assertTrue(event.succeeded());
      startTest[0] = true;
    });
    waitUntil(() -> startTest[0]);
  }

  @Test
  public void asyncMapGet() {
    asyncMap.get("myKey", e -> {
      Assert.assertTrue(e.succeeded());
      Assert.assertEquals("myValue", e.result());
      testComplete();
    });
    await();
  }

  @Test
  public void asyncMapPutIfAbsent() {
    asyncMap.putIfAbsent("myTempKey", "myTempValue", e -> {
      Assert.assertTrue(e.succeeded());
      asyncMap.get("myTempKey", ea -> {
        Assert.assertTrue(e.succeeded());
        Assert.assertEquals("myTempValue", ea.result());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void asyncMapReplace() {
    asyncMap.put("myKey", "myValue", event -> {
      Assert.assertTrue(event.succeeded());
      asyncMap.replace("myKey", "myAnotherValue", e -> {
        Assert.assertTrue(e.succeeded());
        asyncMap.replaceIfPresent("myKey", "myAnotherValue", "myValue", ea -> {
          Assert.assertTrue(ea.succeeded());
          asyncMap.get("myKey", eb -> {
            Assert.assertTrue(eb.succeeded());
            Assert.assertEquals("myValue", eb.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void asyncMapRemoveIfPresent() {
    asyncMap.put("myKey", "myValue", event -> {
      Assert.assertTrue(event.succeeded());
      asyncMap.removeIfPresent("myKey", "myValue", e -> {
        Assert.assertTrue(e.succeeded());
        Assert.assertTrue(e.result());
        vertx.setTimer(1000, timerEvent -> asyncMap.get("myKey", ea -> {
          Assert.assertTrue(ea.succeeded());
          Assert.assertNull(ea.result());
          testComplete();
        }));
      });
    });
    await();
  }

  @Test
  public void asyncMapRemove() {
    asyncMap.put("myKey", "myValue", event -> {
      Assert.assertTrue(event.succeeded());
      asyncMap.remove("myKey", e -> {
        Assert.assertTrue(e.succeeded());
        Assert.assertEquals("myValue", e.result());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void asyncMapClean() {
    asyncMap.put("myAnotherKey", "myValue", event -> {
      Assert.assertTrue(event.succeeded());
      asyncMap.clear(e -> {
        Assert.assertTrue(e.succeeded());
        vertx.setTimer(1000, timerEvent -> asyncMap.get("myAnotherKey", ea -> {
          Assert.assertTrue(ea.succeeded());
          Assert.assertNull(ea.result());
          testComplete();
        }));
      });
    });
    await();
  }


}
