/*
 * Copyright (c) 2011-2013 Red Hat Inc.
 * ------------------------------------
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

package org.vertx.java.platform.impl;

import org.vertx.java.platform.PlatformManager;
import org.vertx.java.platform.PlatformManagerFactory;

/**
 * Don't use this class directly to create PlatformManager instances - instead use the
 * PlatformLocator class
 */
public class DefaultPlatformManagerFactory implements PlatformManagerFactory {

  public PlatformManager createPlatformManager() {
    return new DefaultPlatformManager();
  }

  public PlatformManager createPlatformManager(int clusterPort, String clusterHost) {
    return new DefaultPlatformManager(clusterPort, clusterHost);
  }

  @Override
  public PlatformManager createPlatformManager(int clusterPort, String clusterHost, int quorumSize, String haGroup) {
    return new DefaultPlatformManager(clusterPort, clusterHost, quorumSize, haGroup);
  }
}
