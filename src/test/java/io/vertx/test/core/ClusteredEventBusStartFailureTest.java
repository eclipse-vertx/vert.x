/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 */

package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class ClusteredEventBusStartFailureTest {

  @Test
  public void testCallbackInvokedOnFailure() throws Exception {
    VertxOptions options = new VertxOptions()
      .setClusterManager(new FakeClusterManager())
      .setClusterHost(getClass().getSimpleName());

    AtomicReference<AsyncResult<Vertx>> resultRef = new AtomicReference<>();

    CountDownLatch latch = new CountDownLatch(1);
    Vertx.clusteredVertx(options, ar -> {
      resultRef.set(ar);
      latch.countDown();
    });
    latch.await(5, TimeUnit.SECONDS);

    assertFalse(resultRef.get() == null);
    assertTrue(resultRef.get().failed());
  }
}
