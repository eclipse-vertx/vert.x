/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxOptionsTest extends VertxTestBase {

  @Test
  public void testOptions() {
    VertxOptions options = new VertxOptions();
    assertEquals(2 * Runtime.getRuntime().availableProcessors(), options.getEventLoopPoolSize());
    int rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setEventLoopPoolSize(rand));
    assertEquals(rand, options.getEventLoopPoolSize());
    try {
      options.setEventLoopPoolSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals(20, options.getWorkerPoolSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setWorkerPoolSize(rand));
    assertEquals(rand, options.getWorkerPoolSize());
    try {
      options.setWorkerPoolSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals(20, options.getInternalBlockingPoolSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setInternalBlockingPoolSize(rand));
    assertEquals(rand, options.getInternalBlockingPoolSize());
    try {
      options.setInternalBlockingPoolSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertFalse(options.isClustered());
    assertEquals(options, options.setClustered(true));
    assertTrue(options.isClustered());
    assertEquals(0, options.getClusterPort());
    assertEquals(options, options.setClusterPort(1234));
    assertEquals(1234, options.getClusterPort());
    try {
      options.setClusterPort(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setClusterPort(65536);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals("localhost", options.getClusterHost());
    String randString = TestUtils.randomUnicodeString(100);
    assertEquals(options, options.setClusterHost(randString));
    assertEquals(randString, options.getClusterHost());
    assertEquals(1000, options.getBlockedThreadCheckPeriod());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setBlockedThreadCheckPeriod(rand));
    assertEquals(rand, options.getBlockedThreadCheckPeriod());
    try {
      options.setBlockedThreadCheckPeriod(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals(2000l * 1000000, options.getMaxEventLoopExecuteTime()); // 2 seconds in nano seconds
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setMaxEventLoopExecuteTime(rand));
    assertEquals(rand, options.getMaxEventLoopExecuteTime());
    try {
      options.setMaxEventLoopExecuteTime(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals(1l * 60 * 1000 * 1000000, options.getMaxWorkerExecuteTime()); // 1 minute in nano seconds
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setMaxWorkerExecuteTime(rand));
    assertEquals(rand, options.getMaxWorkerExecuteTime());
    try {
      options.setMaxWorkerExecuteTime(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    ClusterManager mgr = new FakeClusterManager();
    assertNull(options.getClusterManager());
    assertEquals(options, options.setClusterManager(mgr));
    assertSame(mgr, options.getClusterManager());
    assertEquals(10 * 1000, options.getProxyOperationTimeout());
    rand  = TestUtils.randomPositiveInt();
    assertEquals(options, options.setProxyOperationTimeout(rand));
    assertEquals(rand, options.getProxyOperationTimeout());
    try {
      options.setProxyOperationTimeout(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }
}
