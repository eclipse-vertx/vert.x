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

import com.hazelcast.config.Config;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.spi.cluster.impl.hazelcast.HazelcastClusterManager;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ProgrammaticHazelcastClusterManagerTest extends AsyncTestBase {

  static {
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
  }

  private void testProgrammatic(HazelcastClusterManager mgr, Config config) throws Exception {
    mgr.setConfig(config);
    assertEquals(config, mgr.getConfig());
    VertxOptions options = new VertxOptions().setClusterManager(mgr).setClustered(true);
    Vertx.vertxAsync(options, res -> {
      assertTrue(res.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testProgrammaticSetConfig() throws Exception {
    Config config = new Config();
    HazelcastClusterManager mgr = new HazelcastClusterManager();
    mgr.setConfig(config);
    testProgrammatic(mgr, config);
  }

  @Test
  public void testProgrammaticSetWithConstructor() throws Exception {
    Config config = new Config();
    HazelcastClusterManager mgr = new HazelcastClusterManager(config);
    testProgrammatic(mgr, config);
  }
}
