/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.vertx;

import io.netty.channel.EventLoop;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.concurrent.ThreadFactory;

public class ThreadFactoryProviderTest extends AsyncTestBase {

  @Test
  public void testFastThreadLocalFactoryProvider() throws Exception {
    VertxBootstrap factory = VertxBootstrap.create();
    VertxInternal vertx = (VertxInternal) factory
      .threadFactoryProvider((prefix, worker, maxExecuteTime) -> new ThreadFactory() {
        @Override
        public Thread newThread(Runnable target) {
          return new FastThreadLocalThread(target, prefix);
        }
      })
      .init()
      .vertx();
    ContextInternal context = vertx.createEventLoopContext();
    context.runOnContext(v -> {
      assertFalse(Thread.currentThread() instanceof VertxThread);
      assertSame(context, Vertx.currentContext());
      EventLoop eventLoop = context.nettyEventLoop();
      assertTrue(eventLoop.inEventLoop());
      testComplete();
    });
    await();
  }
}
