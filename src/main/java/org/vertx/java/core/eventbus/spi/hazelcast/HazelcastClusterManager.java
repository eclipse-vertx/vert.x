package org.vertx.java.core.eventbus.spi.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.vertx.java.core.eventbus.spi.AsyncMultiMap;
import org.vertx.java.core.eventbus.spi.ClusterManager;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ClusterManager {

  private static final Logger log = LoggerFactory.getLogger(HazelcastClusterManager.class);

  private static HazelcastInstance instance;

  private static synchronized HazelcastInstance getHazelcast() {
    if (instance == null) {
      System.setProperty("hazelcast.mancenter.enabled", "false");
      System.setProperty("hazelcast.memcache.enabled", "false");
      System.setProperty("hazelcast.rest.enabled", "false");
      System.setProperty("hazelcast.wait.seconds.before.join", "0");
      Config cfg;
      InputStream is =
          HazelcastClusterManager.class.getClassLoader().getResourceAsStream("cluster.xml");
      if (is != null) {
        InputStream bis = null;
        try {
          bis = new BufferedInputStream(is);
          cfg = new XmlConfigBuilder(bis).build();
        } finally {
          try {
            if (bis != null) bis.close();
          } catch (Exception ignore) {
          }
        }
      } else {
        log.warn("Cannot find cluster.xml. Using default cluster configuration");
        cfg = null;
      }
      //We use the default instance
      instance = Hazelcast.init(cfg);
    }
    return instance;
  }

  private HazelcastInstance hazelcast;

  public HazelcastClusterManager() {
    hazelcast = getHazelcast();
  }

  public AsyncMultiMap getMultiMap(String name) {
    com.hazelcast.core.MultiMap map = hazelcast.getMultiMap(name);
    return new HazelcastAsyncMultiMap(map);
  }

  public Set getSet(String name) {
    return hazelcast.getSet(name);
  }

  public Map getMap(String name) {
    return hazelcast.getMap(name);
  }

  public void close() {
    Hazelcast.shutdownAll();
  }
}
