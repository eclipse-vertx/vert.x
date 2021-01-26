/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.executor;

import static io.vertx.core.spi.executor.Utils.EXECUTE;
import static io.vertx.core.spi.executor.Utils.SHUTDOWN_NOW;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.WorkerExecutor;
import io.vertx.test.core.BlockedThreadWarning;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;

/**
 * Test a mixture of options being in effect when a SPI implementation of
 * ExecutorServiceFactory is in place
 */
public class ExecutorFactoryOptionsTest extends VertxTestBase {

  private static final String FACTORY = "ExternalLoadableExecutorServiceFactory";
  private static final String EXECUTOR = FACTORY + "$1";

  /*
   * We use a Junit test rule to setup and tear down the classpath to have the
   * correct ExecutorServiceFactory SPI implementation and then check the logs
   * after the tests are run. That means we can inherit all the test setup, tests
   * and tear down from the parent class. We pass in the factory, the name of the
   * executor class that should appear in the logs and a ... of the methods we
   * expect to be used.
   */
  @ClassRule
  public static TestRule chain = Utils.setupAndCheckSpiImpl(FACTORY, EXECUTOR, EXECUTE, SHUTDOWN_NOW);

  /*
   * This rule can be used to check for 'blocking' task messages in the logs
   */
  @Rule
  public BlockedThreadWarning blockedThreadWarning = new BlockedThreadWarning();


  /**
   * Check that verticle deployment options pool name is used with an SPI provided
   * executor.
   */
  @Test
  public void testSpiDeployOptionsDefaultWorkerPoolName() {
    DeploymentOptions options = new DeploymentOptions();
    String poolName = "spiExecutorTest";
    options.setWorkerPoolName(poolName);
    AbstractVerticle v = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> resultHandler) throws Exception {
        vertx.executeBlocking(finished -> {
          Assert.assertTrue(Thread.currentThread().getName().contains(poolName));
          finished.complete();
        }, resultHandler);
      }
    };
    vertx.deployVerticle(v, options, onSuccess(did -> {
      testComplete();
    }));
    await();
  }

  /**
   * Test that pool names work with an SPI provided {@link ExecutorServiceFactory}
   * when a shared worker executor is created
   */
  @Test
  public void testSpiSpecificWorkerPoolName() {
    String specific = "namedPool";
    WorkerExecutor es = vertx.createSharedWorkerExecutor(specific);
    es.executeBlocking(fut -> {
      Assert.assertTrue(Thread.currentThread().getName().contains(specific));
      fut.complete(null);
    }, false, ar -> {
      complete();
    });
    await();
  }

  /**
   * Check that tasks that take too long are allowed to finish but warning
   * messages appear in the log file when verticle deployment options are used
   * under an SPI provided {@link ExecutorServiceFactory}
   */
  @Test
  public void testVerticleDeploymentOptionsMaxTimeSoft() {
    DeploymentOptions options = new DeploymentOptions();
    options.setMaxWorkerExecuteTime(2).setMaxWorkerExecuteTimeUnit(TimeUnit.SECONDS);
    String poolName = "testDeploymentMaxTimeSoft";
    options.setWorkerPoolName(poolName);
    AbstractVerticle sleep = new AbstractVerticle() {
      @Override
      public void start(Promise<Void> resultHandler) throws Exception {
        vertx.executeBlocking(finished -> {
          try {
            SECONDS.sleep(5);
            finished.complete();
          } catch (InterruptedException e) {
            finished.fail(e);
          }
        }, resultHandler);
      }
    };
    vertx.deployVerticle(sleep, options, onSuccess(did -> {
      testComplete();
    }));
    await();
    blockedThreadWarning.expectMessage(poolName, 2000, MILLISECONDS);
  }

  /**
   * Check that tasks that take too long are allowed to finish but warning
   * messages appear in the log file when cross Vertx options are used under an
   * SPI provided {@link ExecutorServiceFactory}
   */
  @Test
  public void testVertxOptionsMaxTimeSoft() {
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(100);
    vertxOptions.setMaxWorkerExecuteTimeUnit(TimeUnit.MILLISECONDS);
    vertxOptions.setBlockedThreadCheckInterval(100);
    vertxOptions.setBlockedThreadCheckIntervalUnit(TimeUnit.MILLISECONDS);

    Vertx newVertx = vertx(vertxOptions);
    Thread mainThread = Thread.currentThread();
    newVertx.executeBlocking(fut -> {
      Thread current = Thread.currentThread();
      assertNotSame(mainThread, current);
      try {
        MILLISECONDS.sleep(200);
        fut.complete();
      } catch (InterruptedException e) {
        fut.fail(e);
      }
    }, true, onSuccess(v -> complete()));
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", 100, MILLISECONDS);
  }

  @Test
  public void testMaxExecuteWorkerTime() throws Exception {
    String poolName = TestUtils.randomAlphaString(10);
    long maxWorkerExecuteTime = NANOSECONDS.convert(3, SECONDS);
    DeploymentOptions deploymentOptions = new DeploymentOptions().setWorkerPoolName(poolName).setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startFuture) throws Exception {
        vertx.executeBlocking(fut -> {
          try {
            SECONDS.sleep(5);
            fut.complete();
          } catch (InterruptedException e) {
            fut.fail(e);
          }
        }, startFuture);
      }
    }, deploymentOptions, onSuccess(did -> {
      testComplete();
    }));
    await();
    blockedThreadWarning.expectMessage(poolName, maxWorkerExecuteTime, NANOSECONDS);
  }
}
