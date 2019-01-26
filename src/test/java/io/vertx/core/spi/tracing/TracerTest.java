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
package io.vertx.core.spi.tracing;

import io.vertx.test.core.VertxTestBase;
import io.vertx.test.faketracer.FakeTracer;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class TracerTest extends VertxTestBase {

  private FakeTracer tracer = new FakeTracer();

  @Override
  protected VertxTracer getTracer() {
    return tracer;
  }

  @Test
  public void testClose() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    assertEquals(0, tracer.closeCount());
    vertx.close(ar -> latch.countDown());
    awaitLatch(latch);
    assertEquals(1, tracer.closeCount());
  }
}
