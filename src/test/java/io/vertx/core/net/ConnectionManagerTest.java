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
package io.vertx.core.net;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.net.impl.clientconnection.ConnectionManager;
import io.vertx.core.net.impl.clientconnection.Endpoint;
import io.vertx.core.net.impl.clientconnection.EndpointProvider;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ConnectionManagerTest extends VertxTestBase {

  private static final Object TEST_KEY = new Object();

  @Test
  public void testGetConnectionSuccess() {
    testGetConnection(true);
  }

  @Test
  public void testGetConnectionFailure() {
    testGetConnection(false);
  }

  private void testGetConnection(boolean success) {
    EventLoopContext ctx = (EventLoopContext) vertx.getOrCreateContext();
    Connection result = new Connection();
    Throwable failure = new Throwable();
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, ContextInternal ctx, Runnable dispose) {
        return new Endpoint<Connection>(dispose) {
          @Override
          public void requestConnection(ContextInternal ctx, Handler<AsyncResult<Connection>> handler) {
            incRefCount();
            if (success) {
              handler.handle(Future.succeededFuture(result));
            } else {
              handler.handle(Future.failedFuture(failure));
            }
          }
        };
      }
    });
    mgr.getConnection(ctx, TEST_KEY, ar -> {
      if (ar.succeeded()) {
        assertTrue(success);
        assertSame(result, ar.result());
      } else {
        assertFalse(success);
        assertSame(failure, ar.cause());
      }
      testComplete();
    });
    await();
  }

  @Test
  public void testDisposeAfterConnectionClose() {
    testDispose(true);
  }

  @Test
  public void testDisposeAfterCallback() {
    testDispose(false);
  }

  private void testDispose(boolean closeConnectionAfterCallback) {
    EventLoopContext ctx = (EventLoopContext) vertx.getOrCreateContext();
    Connection expected = new Connection();
    boolean[] disposed = new boolean[1];
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, ContextInternal ctx, Runnable dispose) {
        return new Endpoint<Connection>(dispose) {
          @Override
          public void requestConnection(ContextInternal ctx, Handler<AsyncResult<Connection>> handler) {
            incRefCount();
            if (closeConnectionAfterCallback) {
              handler.handle(Future.succeededFuture(expected));
              assertFalse(disposed[0]);
              decRefCount();
              assertTrue(disposed[0]);
            } else {
              decRefCount();
              assertFalse(disposed[0]);
              handler.handle(Future.succeededFuture(expected));
              assertTrue(disposed[0]);
            }
          }
          @Override
          protected void dispose() {
            disposed[0] = true;
          }
        };
      }
    });
    mgr.getConnection(ctx, TEST_KEY, onSuccess(conn -> {
      assertEquals(expected, conn);
    }));
    waitUntil(() -> disposed[0]);
  }

  @Test
  public void testCloseManager() throws Exception {
    EventLoopContext ctx = (EventLoopContext) vertx.getOrCreateContext();
    Connection expected = new Connection();
    boolean[] disposed = new boolean[1];
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, ContextInternal ctx, Runnable dispose) {
        return new Endpoint<Connection>(dispose) {
          @Override
          public void requestConnection(ContextInternal ctx, Handler<AsyncResult<Connection>> handler) {
            incRefCount();
            handler.handle(Future.succeededFuture(expected));
          }
          @Override
          protected void dispose() {
            disposed[0] = true;
          }
          @Override
          protected void close() {
            super.close();
            decRefCount();
          }
        };
      }
    });
    CountDownLatch latch = new CountDownLatch(1);
    mgr.getConnection(ctx, TEST_KEY, onSuccess(conn -> {
      assertEquals(expected, conn);
      latch.countDown();
    }));
    awaitLatch(latch);
    assertFalse(disposed[0]);
    mgr.close();
    assertTrue(disposed[0]);
  }

  @Test
  public void testCloseManagerImmediately() {
    EventLoopContext ctx = (EventLoopContext) vertx.getOrCreateContext();
    Connection expected = new Connection();
    boolean[] disposed = new boolean[1];
    AtomicReference<Runnable> adder = new AtomicReference<>();
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, ContextInternal ctx, Runnable dispose) {
        return new Endpoint<Connection>(dispose) {
          @Override
          public void requestConnection(ContextInternal ctx, Handler<AsyncResult<Connection>> handler) {
            adder.set(() -> {
              incRefCount();
            });
          }
        };
      }
    });
    mgr.getConnection(ctx, TEST_KEY, onSuccess(conn -> {
    }));
    waitUntil(() -> adder.get() != null);
    mgr.close();
    adder.get().run();
  }

  @Test
  public void testConcurrentDispose() throws Exception {
    EventLoopContext ctx = (EventLoopContext) vertx.getOrCreateContext();
    ConcurrentLinkedQueue<AtomicBoolean> disposals = new ConcurrentLinkedQueue<>();
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, ContextInternal ctx, Runnable dispose) {
        AtomicBoolean disposed = new AtomicBoolean();
        disposals.add(disposed);
        return new Endpoint<Connection>(dispose) {
          @Override
          public void requestConnection(ContextInternal ctx, Handler<AsyncResult<Connection>> handler) {
            if (disposed.get()) {
              // Check we don't have reentrant demands once disposed
              fail();
            } else {
              Connection conn = new Connection();
              incRefCount();
              handler.handle(Future.succeededFuture(conn));
              decRefCount();
            }
          }
          @Override
          protected void dispose() {
            disposed.set(true);
          }
        };
      }
    });
    int num = 100000;
    int concurrency = 4;
    CountDownLatch[] latches = new CountDownLatch[concurrency];
    for (int i = 0;i < concurrency;i++) {
      CountDownLatch cc = new CountDownLatch(num);
      latches[i] = cc;
      new Thread(() -> {
        for (int j = 0;j < num;j++) {
          mgr.getConnection(ctx, TEST_KEY, onSuccess(conn -> {
            cc.countDown();
          }));
        }
      }).start();
    }
    for (int i = 0;i < concurrency;i++) {
      awaitLatch(latches[i]);
    }
    disposals.forEach(disposed -> {
      waitUntil(disposed::get);
    });
  }

  static class Connection {
  }
}
