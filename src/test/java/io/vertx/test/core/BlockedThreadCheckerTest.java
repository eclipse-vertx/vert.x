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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BlockedThreadCheckerTest extends VertxTestBase {

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
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxEventLoopExecuteTime(1000000000);
    vertxOptions.setWarningExceptionTime(1000000000);
    Vertx newVertx = Vertx.vertx(vertxOptions);
    newVertx.deployVerticle(verticle);
    await();
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
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setMaxWorkerExecuteTime(1000000000);
    vertxOptions.setWarningExceptionTime(1000000000);
    Vertx newVertx = Vertx.vertx(vertxOptions);
    DeploymentOptions depolymentOptions = new DeploymentOptions();
    depolymentOptions.setWorker(true);
    newVertx.deployVerticle(verticle, depolymentOptions);
    await();
  }
}
