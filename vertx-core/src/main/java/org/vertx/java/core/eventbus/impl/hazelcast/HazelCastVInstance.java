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
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/*
  @author <a href="http://tfox.org">Tim Fox</a>
*/
public class HazelCastVInstance {

  private static final Logger log = LoggerFactory.getLogger(HazelCastVInstance.class);

  // Hazelcast config file
  private static final String CONFIG_FILE = "cluster.xml";

  private Config config;

  private HazelcastInstance instance;

  /**
   * Create the singleton Hazelcast instance if necessary
   * @return a hazelcast instance
   */
  public HazelCastVInstance() {
    config = getConfig(null);
    // default instance
    instance = Hazelcast.newHazelcastInstance(config);

    // Properly shutdown all instances
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        Hazelcast.shutdownAll();
      }
    });
  }

  public HazelcastInstance getInstance() {
    return instance;
  }

  /**
   * Get the Hazelcast config
   * @param configfile May be null in which case it gets the default (cluster.xml) will be used.
   * @return a config object
   */
  private static Config getConfig(String configfile) {
    if (configfile == null) {
      configfile = CONFIG_FILE;
    }

    Config cfg = null;
    try (InputStream is = new BufferedInputStream(HazelcastClusterManager.class.getClassLoader().getResourceAsStream(configfile))) {
      if (is != null) {
        cfg = new XmlConfigBuilder(is).build();
      } else {
        log.warn("Cannot find " + configfile + " on classpath. Using default cluster configuration");
      }
    } catch (IOException ex) {
      // ignore
    }
    if (cfg == null) {
      cfg = new Config();
    }
    return cfg;
  }
}
