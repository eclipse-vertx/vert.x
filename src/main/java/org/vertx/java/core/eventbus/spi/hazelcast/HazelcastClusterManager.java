package org.vertx.java.core.eventbus.spi.hazelcast;

import com.hazelcast.core.Hazelcast;
import org.vertx.java.core.eventbus.spi.AsyncMultiMap;
import org.vertx.java.core.eventbus.spi.ClusterManager;

import java.util.Map;
import java.util.Set;

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
    com.hazelcast.core.MultiMap map = Hazelcast.getMultiMap(name);
    return new HazelcastAsyncMultiMap(map);
  }

  public Set getSet(String name) {
    return Hazelcast.getSet(name);
  }

  public Map getMap(String name) {
    return Hazelcast.getMap(name);
  }

  public void close() {
    Hazelcast.shutdownAll();
  }
}
