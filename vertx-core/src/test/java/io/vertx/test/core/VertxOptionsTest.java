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
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.Random;

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
    assertFalse(options.isHAEnabled());
    assertEquals(options, options.setHAEnabled(true));
    assertTrue(options.isHAEnabled());
    rand = TestUtils.randomPositiveInt();
    assertEquals(1, options.getQuorumSize());
    assertEquals(options, options.setQuorumSize(rand));
    assertEquals(rand, options.getQuorumSize());
    try {
      options.setQuorumSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setQuorumSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertNull(options.getHAGroup());
    randString = TestUtils.randomUnicodeString(100);
    assertEquals(options, options.setHAGroup(randString));
    assertEquals(randString, options.getHAGroup());
  }

  @Test
  public void testCopyOptions() {
    VertxOptions options = new VertxOptions();

    int clusterPort = TestUtils.randomPortInt();
    int eventLoopPoolSize = TestUtils.randomPositiveInt();
    int internalBlockingPoolSize = TestUtils.randomPositiveInt();
    int workerPoolSize = TestUtils.randomPositiveInt();
    int blockedThreadCheckPeriod = TestUtils.randomPositiveInt();
    String clusterHost = TestUtils.randomAlphaString(100);
    int maxEventLoopExecuteTime = TestUtils.randomPositiveInt();
    int maxWorkerExecuteTime = TestUtils.randomPositiveInt();
    int proxyOperationTimeout = TestUtils.randomPositiveInt();
    Random rand = new Random();
    boolean haEnabled = rand.nextBoolean();
    int quorumSize = 51214;
    String haGroup = TestUtils.randomAlphaString(100);
    options.setClusterPort(clusterPort);
    options.setEventLoopPoolSize(eventLoopPoolSize);
    options.setInternalBlockingPoolSize(internalBlockingPoolSize);
    options.setWorkerPoolSize(workerPoolSize);
    options.setBlockedThreadCheckPeriod(blockedThreadCheckPeriod);
    options.setClusterHost(clusterHost);
    options.setMaxEventLoopExecuteTime(maxEventLoopExecuteTime);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setProxyOperationTimeout(proxyOperationTimeout);
    options.setHAEnabled(haEnabled);
    options.setQuorumSize(quorumSize);
    options.setHAGroup(haGroup);
    options = new VertxOptions(options);
    assertEquals(clusterPort, options.getClusterPort());
    assertEquals(eventLoopPoolSize, options.getEventLoopPoolSize());
    assertEquals(internalBlockingPoolSize, options.getInternalBlockingPoolSize());
    assertEquals(workerPoolSize, options.getWorkerPoolSize());
    assertEquals(blockedThreadCheckPeriod, options.getBlockedThreadCheckPeriod());
    assertEquals(clusterHost, options.getClusterHost());
    assertEquals(maxEventLoopExecuteTime, options.getMaxEventLoopExecuteTime());
    assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    assertEquals(proxyOperationTimeout, options.getProxyOperationTimeout());
    assertEquals(haEnabled, options.isHAEnabled());
    assertEquals(quorumSize, options.getQuorumSize());
    assertEquals(haGroup, options.getHAGroup());
  }

  @Test
  public void testDefaultJsonOptions() {
    VertxOptions def = new VertxOptions();
    VertxOptions json = new VertxOptions(new JsonObject());
    assertEquals(def.getEventLoopPoolSize(), json.getEventLoopPoolSize());
    assertEquals(def.getWorkerPoolSize(), json.getWorkerPoolSize());
    assertEquals(def.isClustered(), json.isClustered());
    assertEquals(def.getClusterHost(), json.getClusterHost());
    assertEquals(def.getBlockedThreadCheckPeriod(), json.getBlockedThreadCheckPeriod());
    assertEquals(def.getMaxEventLoopExecuteTime(), json.getMaxEventLoopExecuteTime());
    assertEquals(def.getMaxWorkerExecuteTime(), json.getMaxWorkerExecuteTime());
    assertEquals(def.getInternalBlockingPoolSize(), json.getInternalBlockingPoolSize());
    assertEquals(def.getProxyOperationTimeout(), json.getProxyOperationTimeout());
    assertEquals(def.isHAEnabled(), json.isHAEnabled());
    assertEquals(def.getQuorumSize(), json.getQuorumSize());
    assertEquals(def.getHAGroup(), json.getHAGroup());
  }

  @Test
  public void testJsonOptions() {
    VertxOptions options = new VertxOptions(new JsonObject());

    assertEquals(0, options.getClusterPort());
    assertEquals(2 * Runtime.getRuntime().availableProcessors(), options.getEventLoopPoolSize());
    assertEquals(20, options.getInternalBlockingPoolSize());
    assertEquals(20, options.getWorkerPoolSize());
    assertEquals(1000, options.getBlockedThreadCheckPeriod());
    assertEquals("localhost", options.getClusterHost());
    assertEquals(null, options.getClusterManager());
    assertEquals(2000l * 1000000, options.getMaxEventLoopExecuteTime());
    assertEquals(1l * 60 * 1000 * 1000000, options.getMaxWorkerExecuteTime());
    assertEquals(10 * 1000, options.getProxyOperationTimeout());
    assertFalse(options.isHAEnabled());
    assertEquals(1, options.getQuorumSize());
    assertNull(options.getHAGroup());

    int clusterPort = TestUtils.randomPortInt();
    int eventLoopPoolSize = TestUtils.randomPositiveInt();
    int internalBlockingPoolSize = TestUtils.randomPositiveInt();
    int workerPoolSize = TestUtils.randomPositiveInt();
    int blockedThreadCheckPeriod = TestUtils.randomPositiveInt();
    String clusterHost = TestUtils.randomAlphaString(100);
    int maxEventLoopExecuteTime = TestUtils.randomPositiveInt();
    int maxWorkerExecuteTime = TestUtils.randomPositiveInt();
    int proxyOperationTimeout = TestUtils.randomPositiveInt();
    Random rand = new Random();
    boolean haEnabled = rand.nextBoolean();
    int quorumSize = TestUtils.randomShort() + 1;
    String haGroup = TestUtils.randomAlphaString(100);
    options = new VertxOptions(new JsonObject().
        putNumber("clusterPort", clusterPort).
        putNumber("eventLoopPoolSize", eventLoopPoolSize).
        putNumber("internalBlockingPoolSize", internalBlockingPoolSize).
        putNumber("workerPoolSize", workerPoolSize).
        putNumber("blockedThreadCheckPeriod", blockedThreadCheckPeriod).
        putString("clusterHost", clusterHost).
        putNumber("maxEventLoopExecuteTime", maxEventLoopExecuteTime).
        putNumber("maxWorkerExecuteTime", maxWorkerExecuteTime).
        putNumber("proxyOperationTimeout", proxyOperationTimeout).
        putBoolean("haEnabled", haEnabled).
        putNumber("quorumSize", quorumSize).
        putString("haGroup", haGroup)
    );
    assertEquals(clusterPort, options.getClusterPort());
    assertEquals(eventLoopPoolSize, options.getEventLoopPoolSize());
    assertEquals(internalBlockingPoolSize, options.getInternalBlockingPoolSize());
    assertEquals(workerPoolSize, options.getWorkerPoolSize());
    assertEquals(blockedThreadCheckPeriod, options.getBlockedThreadCheckPeriod());
    assertEquals(clusterHost, options.getClusterHost());
    assertEquals(null, options.getClusterManager());
    assertEquals(maxEventLoopExecuteTime, options.getMaxEventLoopExecuteTime());
    assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    assertEquals(proxyOperationTimeout, options.getProxyOperationTimeout());
    assertEquals(haEnabled, options.isHAEnabled());
    assertEquals(quorumSize, options.getQuorumSize());
    assertEquals(haGroup, options.getHAGroup());
  }
}
