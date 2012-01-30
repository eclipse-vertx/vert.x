package org.vertx.java.core.eventbus.spi;

import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ClusterManager {
  AsyncMultiMap getMultiMap(String name);

  Set getSet(String name);

  Map getMap(String name);

  void close();
}
