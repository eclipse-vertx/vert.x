package io.vertx.spi.cluster.impl.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class ZKSyncMapTest {

  @Test
  public void syncMapOperation() {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    CuratorFramework curator = CuratorFrameworkFactory.builder().namespace("io.vertx").connectString("127.0.0.1").retryPolicy(retryPolicy).build();
    curator.start();

    String k = "myKey";
    String v = "myValue";

    ZKSyncMap<String, String> syncMap = new ZKSyncMap<>(curator, "mapTest");

    syncMap.put(k, v);
    assertFalse(syncMap.isEmpty());

    assertEquals(syncMap.get(k), v);

    assertTrue(syncMap.size() > 0);
    assertTrue(syncMap.containsKey(k));
    assertTrue(syncMap.containsValue(v));

    assertTrue(syncMap.keySet().contains(k));
    assertTrue(syncMap.values().contains(v));

    syncMap.entrySet().forEach(entry -> {
      assertEquals(k, entry.getKey());
      assertEquals(v, entry.getValue());
    });

    syncMap.clear();
    assertTrue(syncMap.isEmpty());



  }

  @Test
  public void mockCluster() throws Exception {
    Timing timing = new Timing();
    TestingServer server = new TestingServer();

    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    CuratorFramework curator = CuratorFrameworkFactory.builder().namespace("io.vertx").sessionTimeoutMs(timing.session()).connectionTimeoutMs(timing.connection()).connectString(server.getConnectString()).retryPolicy(retryPolicy).build();
    curator.start();

    String k = "myKey";
    String v = "myValue";

    ZKSyncMap<String, String> syncMap = new ZKSyncMap<>(curator, "mapTest");

    syncMap.put(k, v);
    assertFalse(syncMap.isEmpty());

    assertEquals(syncMap.get(k), v);

    assertTrue(syncMap.size() > 0);
    assertTrue(syncMap.containsKey(k));
    assertTrue(syncMap.containsValue(v));

    assertTrue(syncMap.keySet().contains(k));
    assertTrue(syncMap.values().contains(v));

    syncMap.entrySet().forEach(entry -> {
      assertEquals(k, entry.getKey());
      assertEquals(v, entry.getValue());
    });

    syncMap.clear();
    assertTrue(syncMap.isEmpty());

  }

}
