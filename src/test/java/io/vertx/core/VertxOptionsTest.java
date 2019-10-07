/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

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
    assertEquals(0, options.getEventBusOptions().getPort());
    options.getEventBusOptions().setPort(1234);
    assertEquals(1234, options.getEventBusOptions().getPort());
    try {
      options.getEventBusOptions().setPort(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.getEventBusOptions().setPort(65536);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals(-1, options.getEventBusOptions().getClusterPublicPort());
    options.getEventBusOptions().setClusterPublicPort(1234);
    assertEquals(1234, options.getEventBusOptions().getClusterPublicPort());
    try {
      options.getEventBusOptions().setClusterPublicPort(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.getEventBusOptions().setClusterPublicPort(65536);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals("localhost", options.getEventBusOptions().getHost());
    String randString = TestUtils.randomUnicodeString(100);
    options.getEventBusOptions().setHost(randString);
    assertEquals(randString, options.getEventBusOptions().getHost());
    assertEquals(null, options.getEventBusOptions().getClusterPublicHost());
    randString = TestUtils.randomUnicodeString(100);
    options.getEventBusOptions().setClusterPublicHost(randString);
    assertEquals(randString, options.getEventBusOptions().getClusterPublicHost());
    assertEquals(20000, options.getEventBusOptions().getClusterPingInterval());
    long randomLong = TestUtils.randomPositiveLong();
    options.getEventBusOptions().setClusterPingInterval(randomLong);
    assertEquals(randomLong, options.getEventBusOptions().getClusterPingInterval());
    try {
      options.getEventBusOptions().setClusterPingInterval(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      assertEquals(randomLong, options.getEventBusOptions().getClusterPingInterval());
    }
    assertEquals(20000, options.getEventBusOptions().getClusterPingReplyInterval());
    randomLong = TestUtils.randomPositiveLong();
    options.getEventBusOptions().setClusterPingReplyInterval(randomLong);
    assertEquals(randomLong, options.getEventBusOptions().getClusterPingReplyInterval());
    try {
      options.getEventBusOptions().setClusterPingReplyInterval(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      assertEquals(randomLong, options.getEventBusOptions().getClusterPingReplyInterval());
    }
    assertEquals(1000, options.getBlockedThreadCheckInterval());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setBlockedThreadCheckInterval(rand));
    assertEquals(rand, options.getBlockedThreadCheckInterval());
    try {
      options.setBlockedThreadCheckInterval(0);
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
    assertEquals(VertxOptions.DEFAULT_HA_GROUP, options.getHAGroup());
    randString = TestUtils.randomUnicodeString(100);
    assertEquals(options, options.setHAGroup(randString));
    assertEquals(randString, options.getHAGroup());

    try {
      options.setHAGroup(null);
      fail("Should throw exception");
    } catch (NullPointerException e) {
      // OK
    }
    assertNotNull(options.getMetricsOptions());

    try {
      options.setWarningExceptionTime(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals(options, options.setWarningExceptionTime(1000000000l));
    assertEquals(1000000000l, options.getWarningExceptionTime());

    assertEquals(options, options.setMaxEventLoopExecuteTimeUnit(TimeUnit.SECONDS));
    assertEquals(TimeUnit.SECONDS, options.getMaxEventLoopExecuteTimeUnit());
    assertEquals(options, options.setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS));
    assertEquals(TimeUnit.MILLISECONDS, options.getMaxWorkerExecuteTimeUnit());
    assertEquals(options, options.setWarningExceptionTimeUnit(TimeUnit.MINUTES));
    assertEquals(TimeUnit.MINUTES, options.getWarningExceptionTimeUnit());
    assertEquals(options, options.setBlockedThreadCheckIntervalUnit(TimeUnit.MILLISECONDS));
    assertEquals(TimeUnit.MILLISECONDS, options.getBlockedThreadCheckIntervalUnit());
  }

  @Test
  public void testCopyOptions() {
    VertxOptions options = new VertxOptions();

    int clusterPort = TestUtils.randomPortInt();
    int clusterPublicPort = TestUtils.randomPortInt();
    int eventLoopPoolSize = TestUtils.randomPositiveInt();
    int internalBlockingPoolSize = TestUtils.randomPositiveInt();
    int workerPoolSize = TestUtils.randomPositiveInt();
    int blockedThreadCheckInterval = TestUtils.randomPositiveInt();
    String clusterHost = TestUtils.randomAlphaString(100);
    String clusterPublicHost = TestUtils.randomAlphaString(100);
    long clusterPingInterval = TestUtils.randomPositiveLong();
    long clusterPingReplyInterval = TestUtils.randomPositiveLong();
    int maxEventLoopExecuteTime = TestUtils.randomPositiveInt();
    int maxWorkerExecuteTime = TestUtils.randomPositiveInt();
    Random rand = new Random();
    boolean haEnabled = rand.nextBoolean();
    boolean fileResolverCachingEnabled = rand.nextBoolean();
    boolean metricsEnabled = rand.nextBoolean();
    int quorumSize = 51214;
    String haGroup = TestUtils.randomAlphaString(100);
    long warningExceptionTime = TestUtils.randomPositiveLong();
    TimeUnit maxEventLoopExecuteTimeUnit = TimeUnit.SECONDS;
    TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.MILLISECONDS;
    TimeUnit warningExceptionTimeUnit = TimeUnit.MINUTES;
    TimeUnit blockedThreadCheckIntervalUnit = TimeUnit.MINUTES;
    options.getEventBusOptions().setPort(clusterPort);
    options.getEventBusOptions().setClusterPublicPort(clusterPublicPort);
    options.setEventLoopPoolSize(eventLoopPoolSize);
    options.setInternalBlockingPoolSize(internalBlockingPoolSize);
    options.setWorkerPoolSize(workerPoolSize);
    options.setBlockedThreadCheckInterval(blockedThreadCheckInterval);
    options.getEventBusOptions().setHost(clusterHost);
    options.getEventBusOptions().setClusterPublicHost(clusterPublicHost);
    options.getEventBusOptions().setClusterPingInterval(clusterPingInterval);
    options.getEventBusOptions().setClusterPingReplyInterval(clusterPingReplyInterval);
    options.setMaxEventLoopExecuteTime(maxEventLoopExecuteTime);
    options.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    options.setHAEnabled(haEnabled);
    options.setQuorumSize(quorumSize);
    options.setHAGroup(haGroup);
    options.setMetricsOptions(
        new MetricsOptions().
            setEnabled(metricsEnabled));
    options.setWarningExceptionTime(warningExceptionTime);
    options.setMaxEventLoopExecuteTimeUnit(maxEventLoopExecuteTimeUnit);
    options.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    options.setWarningExceptionTimeUnit(warningExceptionTimeUnit);
    options.setBlockedThreadCheckIntervalUnit(blockedThreadCheckIntervalUnit);

    options = new VertxOptions(options);
    assertEquals(clusterPort, options.getEventBusOptions().getPort());
    assertEquals(clusterPublicPort, options.getEventBusOptions().getClusterPublicPort());
    assertEquals(clusterPingInterval, options.getEventBusOptions().getClusterPingInterval());
    assertEquals(clusterPingReplyInterval, options.getEventBusOptions().getClusterPingReplyInterval());
    assertEquals(eventLoopPoolSize, options.getEventLoopPoolSize());
    assertEquals(internalBlockingPoolSize, options.getInternalBlockingPoolSize());
    assertEquals(workerPoolSize, options.getWorkerPoolSize());
    assertEquals(blockedThreadCheckInterval, options.getBlockedThreadCheckInterval());
    assertEquals(clusterHost, options.getEventBusOptions().getHost());
    assertEquals(clusterPublicHost, options.getEventBusOptions().getClusterPublicHost());
    assertEquals(maxEventLoopExecuteTime, options.getMaxEventLoopExecuteTime());
    assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    assertEquals(haEnabled, options.isHAEnabled());
    assertEquals(quorumSize, options.getQuorumSize());
    assertEquals(haGroup, options.getHAGroup());
    MetricsOptions metricsOptions = options.getMetricsOptions();
    assertNotNull(metricsOptions);
    assertEquals(metricsEnabled, metricsOptions.isEnabled());
    assertEquals(warningExceptionTime, options.getWarningExceptionTime());
    assertEquals(maxEventLoopExecuteTimeUnit, options.getMaxEventLoopExecuteTimeUnit());
    assertEquals(maxWorkerExecuteTimeUnit, options.getMaxWorkerExecuteTimeUnit());
    assertEquals(warningExceptionTimeUnit, options.getWarningExceptionTimeUnit());
    assertEquals(blockedThreadCheckIntervalUnit, options.getBlockedThreadCheckIntervalUnit());
  }

  @Test
  public void testDefaultJsonOptions() {
    VertxOptions def = new VertxOptions();
    VertxOptions json = new VertxOptions(new JsonObject());
    assertEquals(def.getEventLoopPoolSize(), json.getEventLoopPoolSize());
    assertEquals(def.getWorkerPoolSize(), json.getWorkerPoolSize());
    assertEquals(def.getEventBusOptions().getHost(), json.getEventBusOptions().getHost());
    assertEquals(def.getEventBusOptions().getClusterPublicHost(), json.getEventBusOptions().getClusterPublicHost());
    assertEquals(def.getEventBusOptions().getClusterPublicPort(), json.getEventBusOptions().getClusterPublicPort());
    assertEquals(def.getEventBusOptions().getClusterPingInterval(), json.getEventBusOptions().getClusterPingInterval());
    assertEquals(def.getEventBusOptions().getClusterPingReplyInterval(), json.getEventBusOptions().getClusterPingReplyInterval());
    assertEquals(def.getBlockedThreadCheckInterval(), json.getBlockedThreadCheckInterval());
    assertEquals(def.getMaxEventLoopExecuteTime(), json.getMaxEventLoopExecuteTime());
    assertEquals(def.getMaxWorkerExecuteTime(), json.getMaxWorkerExecuteTime());
    assertEquals(def.getInternalBlockingPoolSize(), json.getInternalBlockingPoolSize());
    assertEquals(def.isHAEnabled(), json.isHAEnabled());
    assertEquals(def.getQuorumSize(), json.getQuorumSize());
    assertEquals(def.getHAGroup(), json.getHAGroup());
    assertEquals(def.getWarningExceptionTime(), json.getWarningExceptionTime());
    assertEquals(def.getMaxEventLoopExecuteTimeUnit(), json.getMaxEventLoopExecuteTimeUnit());
    assertEquals(def.getMaxWorkerExecuteTimeUnit(), json.getMaxWorkerExecuteTimeUnit());
    assertEquals(def.getWarningExceptionTimeUnit(), json.getWarningExceptionTimeUnit());
    assertEquals(def.getBlockedThreadCheckIntervalUnit(), json.getBlockedThreadCheckIntervalUnit());
  }

  @Test
  public void testJsonOptions() {
    VertxOptions options = new VertxOptions(new JsonObject());

    assertEquals(0, options.getEventBusOptions().getPort());
    assertEquals(-1, options.getEventBusOptions().getClusterPublicPort());
    assertEquals(20000, options.getEventBusOptions().getClusterPingInterval());
    assertEquals(20000, options.getEventBusOptions().getClusterPingReplyInterval());
    assertEquals(2 * Runtime.getRuntime().availableProcessors(), options.getEventLoopPoolSize());
    assertEquals(20, options.getInternalBlockingPoolSize());
    assertEquals(20, options.getWorkerPoolSize());
    assertEquals(1000, options.getBlockedThreadCheckInterval());
    assertEquals("localhost", options.getEventBusOptions().getHost());
    assertNull(options.getEventBusOptions().getClusterPublicHost());
    assertEquals(null, options.getClusterManager());
    assertEquals(2000l * 1000000, options.getMaxEventLoopExecuteTime());
    assertEquals(1l * 60 * 1000 * 1000000, options.getMaxWorkerExecuteTime());
    assertFalse(options.isHAEnabled());
    assertEquals(1, options.getQuorumSize());
    assertEquals(VertxOptions.DEFAULT_HA_GROUP, options.getHAGroup());
    assertNotNull(options.getMetricsOptions());
    assertEquals(5000000000l, options.getWarningExceptionTime());
    assertEquals(TimeUnit.NANOSECONDS, options.getMaxEventLoopExecuteTimeUnit());
    assertEquals(TimeUnit.NANOSECONDS, options.getMaxWorkerExecuteTimeUnit());
    assertEquals(TimeUnit.NANOSECONDS, options.getWarningExceptionTimeUnit());
    assertEquals(TimeUnit.MILLISECONDS, options.getBlockedThreadCheckIntervalUnit());
    int clusterPort = TestUtils.randomPortInt();
    int clusterPublicPort = TestUtils.randomPortInt();
    int eventLoopPoolSize = TestUtils.randomPositiveInt();
    int internalBlockingPoolSize = TestUtils.randomPositiveInt();
    int workerPoolSize = TestUtils.randomPositiveInt();
    int blockedThreadCheckInterval = TestUtils.randomPositiveInt();
    String clusterHost = TestUtils.randomAlphaString(100);
    String clusterPublicHost = TestUtils.randomAlphaString(100);
    long clusterPingInterval = TestUtils.randomPositiveLong();
    long clusterPingReplyInterval = TestUtils.randomPositiveLong();
    int maxEventLoopExecuteTime = TestUtils.randomPositiveInt();
    int maxWorkerExecuteTime = TestUtils.randomPositiveInt();
    int proxyOperationTimeout = TestUtils.randomPositiveInt();
    long warningExceptionTime = TestUtils.randomPositiveLong();
    Random rand = new Random();
    boolean haEnabled = rand.nextBoolean();
    boolean fileResolverCachingEnabled = rand.nextBoolean();
    int quorumSize = TestUtils.randomShort() + 1;
    String haGroup = TestUtils.randomAlphaString(100);
    boolean classPathResolvingEnabled = rand.nextBoolean();
    boolean metricsEnabled = rand.nextBoolean();
    boolean jmxEnabled = rand.nextBoolean();
    String jmxDomain = TestUtils.randomAlphaString(100);
    TimeUnit maxEventLoopExecuteTimeUnit = TimeUnit.SECONDS;
    TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.MILLISECONDS;
    TimeUnit warningExceptionTimeUnit = TimeUnit.MINUTES;
    TimeUnit blockedThreadCheckIntervalUnit = TimeUnit.MINUTES;
    options = new VertxOptions(new JsonObject().
        put("eventBusOptions", new JsonObject().
          put("port", clusterPort).
          put("clusterPublicPort", clusterPublicPort).
          put("host", clusterHost).
          put("clusterPublicHost", clusterPublicHost).
          put("clusterPingInterval", clusterPingInterval).
          put("clusterPingReplyInterval", clusterPingReplyInterval)).
        put("eventLoopPoolSize", eventLoopPoolSize).
        put("internalBlockingPoolSize", internalBlockingPoolSize).
        put("workerPoolSize", workerPoolSize).
        put("blockedThreadCheckInterval", blockedThreadCheckInterval).
        put("maxEventLoopExecuteTime", maxEventLoopExecuteTime).
        put("maxWorkerExecuteTime", maxWorkerExecuteTime).
        put("proxyOperationTimeout", proxyOperationTimeout).
        put("haEnabled", haEnabled).
        put("fileResolverCachingEnabled", fileResolverCachingEnabled).
        put("quorumSize", quorumSize).
        put("haGroup", haGroup).
        put("warningExceptionTime", warningExceptionTime).
        put("fileSystemOptions", new JsonObject().
            put("classPathResolvingEnabled", classPathResolvingEnabled).
            put("fileCachingEnabled", fileResolverCachingEnabled)).
        put("metricsOptions", new JsonObject().
            put("enabled", metricsEnabled).
            put("jmxEnabled", jmxEnabled).
            put("jmxDomain", jmxDomain)).
        put("maxEventLoopExecuteTimeUnit", maxEventLoopExecuteTimeUnit).
        put("maxWorkerExecuteTimeUnit", maxWorkerExecuteTimeUnit).
        put("warningExceptionTimeUnit", warningExceptionTimeUnit).
        put("blockedThreadCheckIntervalUnit", blockedThreadCheckIntervalUnit)
    );
    assertEquals(clusterPort, options.getEventBusOptions().getPort());
    assertEquals(clusterPublicPort, options.getEventBusOptions().getClusterPublicPort());
    assertEquals(clusterPublicHost, options.getEventBusOptions().getClusterPublicHost());
    assertEquals(clusterPingInterval, options.getEventBusOptions().getClusterPingInterval());
    assertEquals(clusterPingReplyInterval, options.getEventBusOptions().getClusterPingReplyInterval());
    assertEquals(eventLoopPoolSize, options.getEventLoopPoolSize());
    assertEquals(internalBlockingPoolSize, options.getInternalBlockingPoolSize());
    assertEquals(workerPoolSize, options.getWorkerPoolSize());
    assertEquals(blockedThreadCheckInterval, options.getBlockedThreadCheckInterval());
    assertEquals(clusterHost, options.getEventBusOptions().getHost());
    assertEquals(null, options.getClusterManager());
    assertEquals(maxEventLoopExecuteTime, options.getMaxEventLoopExecuteTime());
    assertEquals(maxWorkerExecuteTime, options.getMaxWorkerExecuteTime());
    assertEquals(haEnabled, options.isHAEnabled());
    assertEquals(quorumSize, options.getQuorumSize());
    assertEquals(haGroup, options.getHAGroup());
    FileSystemOptions fileSystemOptions = options.getFileSystemOptions();
    assertEquals(classPathResolvingEnabled, fileSystemOptions.isClassPathResolvingEnabled());
    assertEquals(fileResolverCachingEnabled, fileSystemOptions.isFileCachingEnabled());
    MetricsOptions metricsOptions = options.getMetricsOptions();
    assertEquals(metricsEnabled, metricsOptions.isEnabled());
    assertEquals(warningExceptionTime, options.getWarningExceptionTime());
    assertEquals(maxEventLoopExecuteTimeUnit, options.getMaxEventLoopExecuteTimeUnit());
    assertEquals(maxWorkerExecuteTimeUnit, options.getMaxWorkerExecuteTimeUnit());
    assertEquals(warningExceptionTimeUnit, options.getWarningExceptionTimeUnit());
    assertEquals(blockedThreadCheckIntervalUnit, options.getBlockedThreadCheckIntervalUnit());
  }
}
