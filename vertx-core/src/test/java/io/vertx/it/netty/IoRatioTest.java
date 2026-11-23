/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.it.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * Checks that the {@code io.netty.eventLoop.ioRatio} system property set in the failsafe configuration
 * is applied to the event loops created by Vert.x.
 */
public class IoRatioTest extends VertxTestBase {

  private static final int EXPECTED_IO_RATIO = 50;

  @Test
  public void testIoRatio() {
    assertEquals(String.valueOf(EXPECTED_IO_RATIO), System.getProperty("io.netty.eventLoop.ioRatio"));
    VertxInternal internal = (VertxInternal) vertx;
    assertIoRatio(internal.eventLoopGroup());
    assertIoRatio(internal.acceptorEventLoopGroup());
  }

  private void assertIoRatio(EventLoopGroup group) {
    int count = 0;
    for (EventExecutor executor : group) {
      assertTrue("Unexpected event loop type " + executor.getClass().getName(), executor instanceof SingleThreadIoEventLoop);
      assertEquals(EXPECTED_IO_RATIO, ((SingleThreadIoEventLoop) executor).getIoRatio());
      count++;
    }
    assertTrue(count > 0);
  }
}
