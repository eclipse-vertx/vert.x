package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.zookeeper.ZookeeperClusterManager;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;

/**
 * Mock ZooKeeper Server.
 * Created by stream.Liu
 */
class ZKMockServer {

  private RetryPolicy retryPolicy = new ExponentialBackoffRetry(100, 3);
  private Timing timing;
  private TestingServer server;

  public ZKMockServer() {
    try {
      server = new TestingServer(new InstanceSpec(null, -1, -1, -1, true, -1, -1, 200));
      timing = new Timing();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  ClusterManager getClusterManager() {
    CuratorFramework curator = CuratorFrameworkFactory.builder().namespace("io.vertx").sessionTimeoutMs(10000).connectionTimeoutMs(timing.connection()).connectString(server.getConnectString()).retryPolicy(retryPolicy).build();
    return new ZookeeperClusterManager(retryPolicy, curator);
  }

}
