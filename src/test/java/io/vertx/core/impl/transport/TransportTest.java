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
package io.vertx.core.impl.transport;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxFactory;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.test.core.BlockedThreadWarning;
import io.vertx.test.core.VertxTestBase;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ThreadFactory;

public class TransportTest extends VertxTestBase {

  @Rule
  public BlockedThreadWarning blockedThreadWarning = new BlockedThreadWarning();
  private Transport transport;

  @Override
  public void setUp() throws Exception {
    transport = new Transport() {
      @Override
      public EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio) {
        return super.eventLoopGroup(type, nThreads, null, ioRatio);
      }
    };
    super.setUp();
    disableThreadChecks();
  }

  @Override
  protected Vertx vertx(VertxOptions options) {
    return new VertxFactory(options).transport(transport).vertx();
  }

  @Test
  public void testContext() {
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Thread th = Thread.currentThread();
      assertFalse(th instanceof VertxThread);
      assertTrue(th instanceof FastThreadLocalThread);
//      assertTrue(Context.isOnVertxThread());
//      assertTrue(Context.isOnEventLoopThread());
      ctx.runOnContext(v2 -> {
        assertSame(th, Thread.currentThread());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testExecuteBlocking() {
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Thread th = Thread.currentThread();
      ctx.executeBlocking(p -> {
        assertTrue(Context.isOnWorkerThread());
        p.complete();
      }, ar -> {
        assertSame(th, Thread.currentThread());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testBlockedThreadChecker() {
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      try {
        Thread.sleep(6000);
        testComplete();
      } catch (InterruptedException e) {
        fail(e);
      }
    });
    await();
    blockedThreadWarning.expectMessage("nioEventLoopGroup", VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME, VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT);
  }

  @Test
  public void testSetTimer() {
    vertx.setTimer(100, id -> {
      testComplete();
    });
    await();
  }

  @Test
  public void testHttpServer() {
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Thread th = Thread.currentThread();
      vertx.createHttpServer().requestHandler(req -> {
        assertSame(th, Thread.currentThread());
        Context current = Vertx.currentContext();
        current.runOnContext(v2 -> {
          assertSame(th, Thread.currentThread());
          assertSame(current, Vertx.currentContext());
          req.response().end();
        });
      }).listen(8080, onSuccess(s -> {
        HttpClient client = vertx.createHttpClient();
        client.get(8080, "localhost", "/", onSuccess(resp -> {
          testComplete();
        }));
      }));
    });
    await();
  }

  @Test
  public void testCreateContextFromEventLoop() {
    EventLoop eventLoop = vertx.nettyEventLoopGroup().next();
    ContextInternal ctx = ((VertxInternal) vertx).createEventLoopContext(eventLoop, null, null);
    ctx.runOnContext(v -> {
      vertx.setTimer(10, id -> {
        assertSame(ctx, Vertx.currentContext());
        testComplete();
      });
    });
    await();
  }
}
