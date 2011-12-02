package org.vertx.java.core.cluster.spi.hazelcast;

import com.hazelcast.core.Hazelcast;
import org.vertx.java.core.cluster.spi.AsyncMultiMap;
import org.vertx.java.core.cluster.spi.ClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ClusterManager {

  public HazelcastClusterManager() {
    System.setProperty("hazelcast.mancenter.enabled", "false");
    System.setProperty("hazelcast.memcache.enabled", "false");
    System.setProperty("hazelcast.rest.enabled", "false");
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
  }

  public AsyncMultiMap getMultiMap(String name) {
    com.hazelcast.core.MultiMap map = Hazelcast.getMultiMap("subs");
    return new HazelcastAsyncMultiMap(map);
  }

  public void close() {
    Hazelcast.shutdownAll();
  }
}
