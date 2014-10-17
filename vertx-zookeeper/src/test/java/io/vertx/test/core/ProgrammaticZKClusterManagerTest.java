package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.zookeeper.MockZKCluster;
import io.vertx.spi.cluster.impl.zookeeper.ZookeeperClusterManager;
import org.junit.Test;

import java.util.Properties;

/**
 * Created by Stream.Liu
 */
public class ProgrammaticZKClusterManagerTest extends AsyncTestBase {

  @Test
  public void testProgrammatic() throws Exception {
    MockZKCluster zkCluster = new MockZKCluster();
    ClusterManager clusterManager = zkCluster.getClusterManager();

    Properties zkConfig = new Properties();
    zkConfig.setProperty("hosts.zookeeper", "127.0.0.1");
    zkConfig.setProperty("path.root", "io.vertx");
    zkConfig.setProperty("retry.initialSleepTime", "1000");
    zkConfig.setProperty("retry.intervalTimes", "3");
    ZookeeperClusterManager zcl = new ZookeeperClusterManager(zkConfig);
    zcl.setConfig(zkConfig);
    assertEquals(zkConfig, zcl.getConfig());
    VertxOptions options = new VertxOptions().setClusterManager(clusterManager).setClustered(true);
    Vertx.vertxAsync(options, res -> {
      assertTrue(res.succeeded());
      testComplete();
    });
    await();
  }
}
