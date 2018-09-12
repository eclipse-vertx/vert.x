/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.core.*;
import io.vertx.test.core.BlockedThreadWarning;
import io.vertx.test.core.VertxTestBase;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

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
    blockedThreadWarning.expectMessage("vert.x-eventloop-thread", VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME, VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT);
  }

  @Test
  public void testBlockCheckExceptionTimeLimit() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(3000);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxEventLoopExecuteTime = 1;
    TimeUnit maxEventLoopExecuteTimeUnit = SECONDS;
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxEventLoopExecuteTime(maxEventLoopExecuteTime);
    vertxOptions.setMaxEventLoopExecuteTimeUnit(maxEventLoopExecuteTimeUnit);
    vertxOptions.setWarningExceptionTime(maxEventLoopExecuteTime);
    vertxOptions.setWarningExceptionTimeUnit(maxEventLoopExecuteTimeUnit);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-eventloop-thread", maxEventLoopExecuteTime, maxEventLoopExecuteTimeUnit);
  }

  @Test
  public void testBlockCheckWorker() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(3000);
        testComplete();
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxWorkerExecuteTime = 1;
    TimeUnit maxWorkerExecuteTimeUnit = SECONDS;
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
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTime, maxWorkerExecuteTimeUnit);
  }

  @Test
  public void testBlockCheckExecuteBlocking() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        vertx.executeBlocking(fut -> {
          try {
            Thread.sleep(3000);
          } catch (InterruptedException e) {
            fail();
          }
          testComplete();
        }, ar -> {});
      }
    };
    // set warning threshold to 1s and the exception threshold as well
    long maxWorkerExecuteTime = 1;
    TimeUnit maxWorkerExecuteTimeUnit = SECONDS;
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    vertxOptions.setWarningExceptionTime(maxWorkerExecuteTime);
    vertxOptions.setWarningExceptionTimeUnit(maxWorkerExecuteTimeUnit);
    Vertx newVertx = vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
    blockedThreadWarning.expectMessage("vert.x-worker-thread", maxWorkerExecuteTime, maxWorkerExecuteTimeUnit);
  }
}
