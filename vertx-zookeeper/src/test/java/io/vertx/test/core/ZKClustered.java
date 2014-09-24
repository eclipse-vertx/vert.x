package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.zookeeper.ZookeeperClusterManager;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;

/**
 * Created by stream.Liu
 */
class ZKClustered {

  private ClusterManager zkClusterManager;

  ClusterManager getClusterManager() {
    if (zkClusterManager == null) {
      try {
        TestingServer server = new TestingServer();
        Timing timing = new Timing();
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(100, 3);
        CuratorFramework curator = CuratorFrameworkFactory.builder().namespace("io.vertx").sessionTimeoutMs(timing.session()).connectionTimeoutMs(timing.connection()).connectString(server.getConnectString()).retryPolicy(retryPolicy).build();
        zkClusterManager = new ZookeeperClusterManager(retryPolicy, curator);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return zkClusterManager;
  }

}
