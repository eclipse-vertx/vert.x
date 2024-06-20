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
package io.vertx.core.spi.tracing;

import io.vertx.core.WorkerExecutor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.faketracer.FakeTracer;
import org.junit.Test;

public class TracerTest extends VertxTestBase {

  private FakeTracer tracer = new FakeTracer();

  @Override
  protected VertxTracer getTracer() {
    return tracer;
  }

  @Test
  public void testClose() throws Exception {
    assertEquals(0, tracer.closeCount());
    awaitFuture(vertx.close());
    assertEquals(1, tracer.closeCount());
  }

  @Test
  public void testWorkerExecutor() {
    WorkerExecutor exec = vertx.createSharedWorkerExecutor("exec");
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal duplicate = ctx.duplicate();
    duplicate.runOnContext(v -> {
      exec.executeBlocking(() -> null).onComplete(onSuccess(res -> {
        testComplete();
      }));
    });
    await();
  }
}
