/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.hazelcast.HazelcastClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastComplexHATest extends ComplexHATest {

  static {
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager();
  }

}
