package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;

/**
 *
 */
public class ZKClusteredEventbusTest extends ClusteredEventBusTest {

  private ZKClustered zkClustered = new ZKClustered();

  @Override
  protected ClusterManager getClusterManager() {
    return zkClustered.getClusterManager();
  }

}
