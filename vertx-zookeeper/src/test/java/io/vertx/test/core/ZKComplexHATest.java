package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.zookeeper.ZookeeperClusterManager;

/**
 * Created by stream on 9/15/14.
 */
public class ZKComplexHATest extends ComplexHATest {

  @Override
  protected ClusterManager getClusterManager() {
    return new ZookeeperClusterManager();
  }
}
