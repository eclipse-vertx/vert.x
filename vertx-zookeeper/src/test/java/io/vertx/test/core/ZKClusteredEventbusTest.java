package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;

/**
 *
 */
public class ZKClusteredEventbusTest extends ClusteredEventBusTest {

  private ZKMockServer zkClustered = new ZKMockServer();

  @Override
  protected ClusterManager getClusterManager() {
    return zkClustered.getClusterManager();
  }

}
