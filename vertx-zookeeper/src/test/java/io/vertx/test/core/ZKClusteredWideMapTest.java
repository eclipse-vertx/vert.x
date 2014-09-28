package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;

/**
 *
 */
public class ZKClusteredWideMapTest extends ClusterWideMapTestDifferentNodes {

  private ZKClustered zkClustered = new ZKClustered();

  @Override
  protected ClusterManager getClusterManager() {
    return zkClustered.getClusterManager();
  }


}
