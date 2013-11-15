/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.vertx.java.platform;

/**
 *
 * Factory for creating PlatformManager instances. Use this when embedding the Vert.x platform.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public interface PlatformManagerFactory {

  /**
   * Create a non clustered platform manager
   * @return The instance
   */
  PlatformManager createPlatformManager();

  /**
   * Create a clustered platform manager
   * @param clusterPort The cluster port to listen on
   * @param clusterHost The cluster host to listen on
   * @return The instance
   */
  PlatformManager createPlatformManager(int clusterPort, String clusterHost);

  /**
   * Create a clustered platform manager with HA enabled
   * @param clusterPort The cluster port to listen on
   * @param clusterHost The cluster host to listen on
   * @param quorumSize The minimum number of nodes in the cluster before deployments will be activated
   * @param haGroup The HA group this Vert.x instance participates in. If null defaults to __DEFAULT__
   * @return The instance
   */
  PlatformManager createPlatformManager(int clusterPort, String clusterHost, int quorumSize, String haGroup);
}
