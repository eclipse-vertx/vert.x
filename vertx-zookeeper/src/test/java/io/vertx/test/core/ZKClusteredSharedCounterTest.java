package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;

/**
 *
 */
public class ZKClusteredSharedCounterTest extends ClusteredSharedCounterTest {

  private ZKClustered zkClustered = new ZKClustered();

  @Override
  protected ClusterManager getClusterManager() {
    return zkClustered.getClusterManager();
  }
}
