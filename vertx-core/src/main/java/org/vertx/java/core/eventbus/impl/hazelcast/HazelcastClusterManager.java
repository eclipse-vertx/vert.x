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
import com.hazelcast.core.MultiMap;
import org.vertx.java.core.eventbus.impl.ClusterManager;
import org.vertx.java.core.eventbus.impl.SubsMap;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A cluster manager based on a HazelcastInstance singleton.
 * <p>
 * Please be aware of the typical issues with singletons. E.g. all junit tests will 
 * share a single instance and the data (subsMap).
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastClusterManager implements ClusterManager {

  private static final Logger log = LoggerFactory.getLogger(HazelcastClusterManager.class);

  // Hazelcast config file
  private static final String CONFIG_FILE = "cluster.xml";
  
  // Default instance (singleton)
  private static HazelcastInstance instance;

  private final VertxInternal vertx;

  /**
   * Constructor
   */
  public HazelcastClusterManager(final VertxInternal vertx) {
  	this.vertx = vertx;
    initHazelcast();
  }

  /**
   * Create the singleton Hazelcast instance if necessary
   * @return a hazelcast instance
   */
  private synchronized HazelcastInstance initHazelcast() {
    if (instance == null) {
      Config cfg = getConfig(null);
      if (cfg == null) {
        log.warn("Cannot find cluster.xml on classpath. Using default cluster configuration");
      }

      // default instance
      instance = Hazelcast.newHazelcastInstance(cfg);

      // Properly shutdown all instances
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          Hazelcast.shutdownAll();
        }
      });
    }

    return instance;
  }

  /**
   * Get the Hazelcast config
   * @param configfile May be null in which case it gets the default (cluster.xml) will be used.
   * @return a config object
   */
	protected Config getConfig(String configfile) {
		if (configfile == null) {
			configfile = CONFIG_FILE;
		}

		Config cfg = null;
		try (InputStream is = HazelcastClusterManager.class.getClassLoader().getResourceAsStream(configfile);
		    InputStream bis = new BufferedInputStream(is)) {
			if (is != null) {
				cfg = new XmlConfigBuilder(bis).build();
			}
		} catch (IOException ex) {
			// ignore
		}
		return cfg;
	}

	/**
	 * Every eventbus handler has an ID. SubsMap (subscriber map) is a MultiMap which 
	 * maps handler-IDs with server-IDs and thus allows the eventbus to determine where 
	 * to send messages.
	 * 
	 * @param name A unique name by which the the MultiMap can be identified within the cluster. 
	 *     See the cluster config file (e.g. cluster.xml in case of HazelcastClusterManager) for
	 *     additional MultiMap config parameters.
	 * @return subscription map
	 */
  public SubsMap getSubsMap(final String name) {
    MultiMap<String, HazelcastServerID> map = instance.getMultiMap(name);
    return new HazelcastSubsMap(vertx, map);
  }

  /**
   * Because it implements a singleton, close() needs to be a noop
   */
  public void close() {
 		// hazelcast.getLifecycleService().shutdown();
  }
  
  /**
   * Provide access to the singleton Hazelcast instance, e.g. to properly close it.
   * @return hazelcast instance
   */
  public HazelcastInstance getInstance() {
  	return instance;
  }
}
