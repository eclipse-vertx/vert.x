/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 * You may elect to redistribute this code under either of these licenses.
 */
package org.vertx.java.spi.cluster.impl.hazelcast;

import com.hazelcast.config.Config;
import org.vertx.java.core.spi.VertxSPI;
import org.vertx.java.core.spi.cluster.ClusterManager;
import org.vertx.java.core.spi.cluster.ClusterManagerFactory;

/**
 * Class to allow the programmatic configuration of a Hazelcast cluster in vert.x embedded.
 *
 * @author <a href="http://www.p14n.com">Dean Pehrsson-Chapman</a>
 */
public class ProgrammableClusterManagerFactory implements ClusterManagerFactory {

  private volatile static Config config;

  @Override
  public ClusterManager createClusterManager(VertxSPI vertx) {
    return new HazelcastClusterManager(vertx) {
      @Override
      protected Config getConfig() {
        return config;
      }
    };
  }

  /**
   * Sets the Config object to be used for clustering.  Call this method before creating the PlatformManager.
   */
  public static void setConfig(Config config) {
    ProgrammableClusterManagerFactory.config = config;
  }
}