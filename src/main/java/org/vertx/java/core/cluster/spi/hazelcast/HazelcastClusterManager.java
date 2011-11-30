package org.vertx.java.core.cluster.spi.hazelcast;

import com.hazelcast.core.Hazelcast;
import org.vertx.java.core.cluster.spi.ClusterManager;
import org.vertx.java.core.cluster.spi.AsyncMultiMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ClusterManager {

  public HazelcastClusterManager() {
//    Config cfg = new Config();
//    cfg.setPort(5900);
//    cfg.setPortAutoIncrement(false);
//
//    NetworkConfig network = cfg.getNetworkConfig();
//    Join join = network.getJoin();
//    join.getTcpIpConfig().addMember("10.45.67.32").addMember("10.45.67.100")
//                .setRequiredMember("192.168.10.100").setEnabled(true);
//    network.getInterfaces().setEnabled(true).addInterface("10.45.67.*");
//
//    MapConfig mapCfg = new MapConfig();
//    mapCfg.setName("testMap");
//    mapCfg.setBackupCount(2);
//    mapCfg.getMaxSizeConfig().setSize(10000);
//    mapCfg.setTimeToLiveSeconds(300);
//
//
//
////    MapStoreConfig mapStoreCfg = new MapStoreConfig();
////    mapStoreCfg.setClassName("com.cluster.examples.DummyStore").setEnabled(true);
////    mapCfg.setMapStoreConfig(mapStoreCfg);
//
////    NearCacheConfig nearCacheConfig = new NearCacheConfig();
////    nearCacheConfig.setMaxSize(1000).setMaxIdleSeconds(120).setTimeToLiveSeconds(300);
////    mapCfg.setNearCacheConfig(nearCacheConfig);
//
//    cfg.addMapConfig(mapCfg);
  }

  public AsyncMultiMap getMultiMap(String name) {
    com.hazelcast.core.MultiMap map = Hazelcast.getMultiMap("subs");
    return new HazelcastAsyncMultiMap(map);
  }
}
