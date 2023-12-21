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

import io.vertx.core.Context;
import io.vertx.core.impl.ContextInternal;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class LocalEventBusTracerTest extends EventBusTracerTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    vertx1 = vertx;
    vertx2 = vertx;
  }

  @Ignore("Cannot pass for now")
  @Test
  public void testInboundInterceptor() throws Exception {
    tracer = new VertxTracer() {};
    vertx2.eventBus().addInboundInterceptor(deliveryCtx -> {
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
//      ctx.putLocal(FakeTracer.ACTIVE_SCOPE_KEY, "val");
      deliveryCtx.next();
    });
    Context receiveCtx = vertx2.getOrCreateContext();
    CountDownLatch latch = new CountDownLatch(1);
    receiveCtx.runOnContext(v -> {
      vertx2.eventBus().consumer("the_address", msg -> {
//        Object val = vertx.getOrCreateContext().getLocal(FakeTracer.ACTIVE_SCOPE_KEY);
//        assertEquals("val", val);
        testComplete();
      });
      latch.countDown();
    });
    awaitLatch(latch);
    vertx1.eventBus().send("the_address", "msg");
    await();
  }
}
