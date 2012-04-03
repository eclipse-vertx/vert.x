/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.eventbus.impl.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.vertx.java.core.eventbus.impl.ClusterManager;
import org.vertx.java.core.eventbus.impl.SubsMap;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.InputStream;

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
        log.warn("Cannot find cluster.xml on classpath. Using default cluster configuration");
        cfg = null;
      }
      //We use the default instance
      instance = Hazelcast.init(cfg);
    }
    return instance;
  }

  private final VertxInternal vertx;
  private final HazelcastInstance hazelcast;

  public HazelcastClusterManager(VertxInternal vertx) {
    this.vertx = vertx;
    hazelcast = getHazelcast();
  }

  public SubsMap getSubsMap(String name) {
    com.hazelcast.core.MultiMap map = hazelcast.getMultiMap(name);
    return new HazelcastSubsMap(vertx, map);
  }

  public void close() {
    Hazelcast.shutdownAll();
  }
}
