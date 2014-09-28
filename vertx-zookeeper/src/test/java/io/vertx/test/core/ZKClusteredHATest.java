package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;

/**
 * Created by stream.Liu
 */
public class ZKClusteredHATest extends HATest {

  private ZKClustered zkClustered = new ZKClustered();

  @Override
  protected ClusterManager getClusterManager() {
    return zkClustered.getClusterManager();
  }
}
