/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class BlockedThreadCheckerTest extends VertxTestBase {

  @Rule
  public BlockedThreadWarning blockedThreadWarning = new BlockedThreadWarning();

  @Test
  public void testBlockCheckDefault() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(6000);
        testComplete();
      }
    };
    vertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-eventloop-thread", VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME);
  }

  @Test
  public void testBlockCheckExceptionTimeLimit() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(2000);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxEventLoopExecuteTime = NANOSECONDS.convert(1, SECONDS);
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxEventLoopExecuteTime(maxEventLoopExecuteTime);
    vertxOptions.setWarningExceptionTime(maxEventLoopExecuteTime);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-eventloop-thread", maxEventLoopExecuteTime);
  }

  @Test
  public void testBlockCheckWorker() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(2000);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxWorkerExecuteTime = NANOSECONDS.convert(1, SECONDS);
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setWarningExceptionTime(maxWorkerExecuteTime);
    Vertx newVertx = vertx(vertxOptions);
    DeploymentOptions deploymentOptions = new DeploymentOptions();
    deploymentOptions.setWorker(true);
    newVertx.deployVerticle(verticle, deploymentOptions);
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTime);
  }

  @Test
  public void testBlockCheckExecuteBlocking() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        vertx.executeBlocking(fut -> {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            fail();
          }
          testComplete();
        }, ar -> {});
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxWorkerExecuteTime = NANOSECONDS.convert(1, SECONDS);
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setWarningExceptionTime(maxWorkerExecuteTime);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTime);
  }


  @Test
  public void testBlockCheckExceptionTimeLimitWithTimeUnit() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxEventLoopExecuteTime = 1;
    TimeUnit maxEventLoopExecuteTimeUnit = TimeUnit.SECONDS;
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxEventLoopExecuteTimeUnit(maxEventLoopExecuteTimeUnit);
    vertxOptions.setMaxEventLoopExecuteTime(maxEventLoopExecuteTime);
    vertxOptions.setWarningExceptionTimeUnit(maxEventLoopExecuteTimeUnit);
    vertxOptions.setWarningExceptionTime(maxEventLoopExecuteTime);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-eventloop-thread", maxEventLoopExecuteTimeUnit.toNanos(maxEventLoopExecuteTime));
  }

  @Test
  public void testBlockCheckWorkerWithTimeUnit() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxWorkerExecuteTime = 1;
    TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.SECONDS;
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    vertxOptions.setWarningExceptionTime(maxWorkerExecuteTime);
    vertxOptions.setWarningExceptionTimeUnit(maxWorkerExecuteTimeUnit);
    Vertx newVertx = vertx(vertxOptions);
    DeploymentOptions deploymentOptions = new DeploymentOptions();
    deploymentOptions.setWorker(true);
    newVertx.deployVerticle(verticle, deploymentOptions);
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTimeUnit.toNanos(maxWorkerExecuteTime));
  }

  @Test
  public void testBlockCheckExecuteBlockingWithTimeUnit() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() {
        vertx.executeBlocking(fut -> {
          try {
            TimeUnit.SECONDS.sleep(2);
          } catch (InterruptedException e) {
            fail();
          }
          testComplete();
        }, ar -> {});
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxWorkerExecuteTime = 1;
    TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.SECONDS;
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    vertxOptions.setWarningExceptionTime(maxWorkerExecuteTime);
    vertxOptions.setWarningExceptionTimeUnit(maxWorkerExecuteTimeUnit);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTimeUnit.toNanos(maxWorkerExecuteTime));
  }
}
