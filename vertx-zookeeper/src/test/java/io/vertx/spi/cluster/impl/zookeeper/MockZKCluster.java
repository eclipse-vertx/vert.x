package io.vertx.spi.cluster.impl.zookeeper;

import io.vertx.core.spi.cluster.ClusterManager;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;

/**
 * Created by Stream.Liu
 */
public class MockZKCluster {

  private RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 5);
  private Timing timing;
  private TestingServer server;

  public MockZKCluster() {
    try {
      server = new TestingServer(new InstanceSpec(null, -1, -1, -1, true, -1, -1, 100), true);
      timing = new Timing();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public ClusterManager getClusterManager() {
    CuratorFramework curator = CuratorFrameworkFactory.builder().namespace("io.vertx").sessionTimeoutMs(60000).connectionTimeoutMs(timing.connection()).connectString(server.getConnectString()).retryPolicy(retryPolicy).build();
    return new ZookeeperClusterManager(retryPolicy, curator);
  }
}
