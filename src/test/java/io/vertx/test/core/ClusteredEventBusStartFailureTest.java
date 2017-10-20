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
 */

package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Thomas Segismont
 */
public class ClusteredEventBusStartFailureTest extends AsyncTestBase {

  @Test
  public void testCallbackInvokedOnFailure() throws Exception {

    // will trigger java.net.UnknownHostException
    String hostName = getClass().getSimpleName();

    VertxOptions options = new VertxOptions()
      .setClusterManager(new FakeClusterManager())
      .setClusterHost(hostName);

    AtomicReference<AsyncResult<Vertx>> resultRef = new AtomicReference<>();

    CountDownLatch latch = new CountDownLatch(1);
    Vertx.clusteredVertx(options, ar -> {
      resultRef.set(ar);
      latch.countDown();
    });
    awaitLatch(latch);

    assertFalse(resultRef.get() == null);
    assertTrue(resultRef.get().failed());
    assertTrue("Was expecting failure to be an instance of UnknownHostException", resultRef.get().cause() instanceof UnknownHostException);
  }
}
