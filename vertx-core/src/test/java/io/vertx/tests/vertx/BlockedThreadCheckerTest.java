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

package io.vertx.tests.vertx;

import io.vertx.core.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.threadchecker.BlockedThreadEvent;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
public class BlockedThreadCheckerTest extends VertxTestBase {

  private final List<BlockedThreadEvent> events = Collections.synchronizedList(new ArrayList<>());

  public void expectMessage(String poolName, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    List<BlockedThreadEvent> copy;
    synchronized (events) {
      copy = new ArrayList<>(events);
    }
    boolean match = false;
    for (BlockedThreadEvent event : copy) {
      if (event.thread().getName().startsWith(poolName) &&
        event.maxExecTime() == maxExecuteTimeUnit.toNanos(maxExecuteTime)) {
        match = true;
        break;
      }
    }
    assertTrue("Invalid events: " + copy, match);
  }

  private void catchBlockedThreadEvents(Vertx vertx) {
    ((VertxInternal)vertx).blockedThreadChecker().setThreadBlockedHandler(event -> events.add(event));
  }

  @Test
  public void testBlockCheckDefault() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(6000);
        testComplete();
      }
    };
    catchBlockedThreadEvents(vertx);
    vertx.deployVerticle(verticle);
    await();
    expectMessage("vert.x-eventloop-thread", VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME, VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT);
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
    catchBlockedThreadEvents(newVertx);
    try {
      newVertx.deployVerticle(verticle);
      await();
      expectMessage("vert.x-eventloop-thread", maxEventLoopExecuteTime, maxEventLoopExecuteTimeUnit);
    } finally {
      newVertx.close();
    }
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
    try {
      catchBlockedThreadEvents(newVertx);
      DeploymentOptions deploymentOptions = new DeploymentOptions();
      deploymentOptions.setThreadingModel(ThreadingModel.WORKER);
      newVertx.deployVerticle(verticle, deploymentOptions);
      await();
      expectMessage("vert.x-worker-thread", maxWorkerExecuteTime, maxWorkerExecuteTimeUnit);
    } finally {
      newVertx.close();
    }
  }

  @Test
  public void testBlockCheckExecuteBlocking() throws Exception {
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        vertx.executeBlocking(() -> {
          try {
            Thread.sleep(3000);
          } catch (InterruptedException e) {
            fail();
          }
          testComplete();
          return null;
        });
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
    catchBlockedThreadEvents(newVertx);
    try {
      newVertx.deployVerticle(verticle);
      await();
      expectMessage("vert.x-worker-thread", maxWorkerExecuteTime, maxWorkerExecuteTimeUnit);
    } finally {
      newVertx.close();
    }
  }

  @Test
  public void testNamedWorkerPoolMaxExecuteWorkerTime() {
    String poolName = TestUtils.randomAlphaString(10);
    long maxWorkerExecuteTime = NANOSECONDS.convert(3, SECONDS);
    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setWorkerPoolName(poolName)
      .setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    catchBlockedThreadEvents(vertx);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        vertx.<Void>executeBlocking(() -> {
          SECONDS.sleep(5);
          return null;
        }).onComplete(startPromise);
      }
    }, deploymentOptions).onComplete(onSuccess(did -> {
      testComplete();
    }));
    await();
    expectMessage(poolName, maxWorkerExecuteTime, NANOSECONDS);
  }

  @Test
  public void testCustomThreadBlockedHandler() throws Exception {
    disableThreadChecks();
    waitFor(2);
    Verticle verticle = new AbstractVerticle() {
      @Override
      public void start() throws InterruptedException {
        Thread.sleep(3000);
        complete();
      }
    };
    long maxWorkerExecuteTime = 1000;
    long warningExceptionTime = 2;
    TimeUnit maxWorkerExecuteTimeUnit = MILLISECONDS;
    TimeUnit warningExceptionTimeUnit = SECONDS;
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertxOptions.setMaxWorkerExecuteTimeUnit(maxWorkerExecuteTimeUnit);
    vertxOptions.setWarningExceptionTime(warningExceptionTime);
    vertxOptions.setWarningExceptionTimeUnit(warningExceptionTimeUnit);
    Vertx newVertx = vertx(vertxOptions);
    ((VertxInternal) newVertx).blockedThreadChecker().setThreadBlockedHandler(bte -> {
      assertEquals(NANOSECONDS.convert(maxWorkerExecuteTime, maxWorkerExecuteTimeUnit), bte.maxExecTime());
      assertEquals(NANOSECONDS.convert(warningExceptionTime, warningExceptionTimeUnit), bte.warningExceptionTime());
      complete();
    });
    DeploymentOptions deploymentOptions = new DeploymentOptions();
    deploymentOptions.setThreadingModel(ThreadingModel.WORKER);
    newVertx.deployVerticle(verticle, deploymentOptions);
    await();
  }
}
