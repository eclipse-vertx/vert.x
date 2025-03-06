/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.virtualthread;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.WorkerExecutor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import io.vertx.tests.deployment.VirtualThreadDeploymentTest;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VirtualThreadContextTest extends VirtualThreadContextTestBase {

  protected ContextInternal createVirtualThreadContext() {
    return vertx.createVirtualThreadContext();
  }

  @Test
  public void testSerializeBlocking() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    AtomicInteger inflight = new AtomicInteger();
    createVirtualThreadContext().runOnContext(v1 -> {
      Context ctx = vertx.getOrCreateContext();
      for (int i = 0;i < 2;i++) {
        ctx.runOnContext(v2 -> sleep(inflight));
      }
      ctx.runOnContext(v -> testComplete());
    });
    await();
  }

  private void sleep(AtomicInteger inflight) {
    assertEquals(0, inflight.getAndIncrement());
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      inflight.decrementAndGet();
    }
  }
}
