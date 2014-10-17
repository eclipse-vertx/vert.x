package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.zookeeper.MockZKCluster;

/**
 * Created by stream.Liu
 */
public class ZKClusteredHATest extends HATest {

  private MockZKCluster zkClustered = new MockZKCluster();

  @Override
  protected ClusterManager getClusterManager() {
    return zkClustered.getClusterManager();
  }
}
