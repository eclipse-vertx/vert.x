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
package io.vertx.core.net.impl.pool;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
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
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Connection result = new Connection();
    Throwable failure = new Throwable();
    EndpointProvider<Object, Connection> provider = new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, Runnable dispose) {
        return new Endpoint<Connection>(dispose) {
          @Override
          public Future<Connection> requestConnection(ContextInternal ctx, long timeout) {
            incRefCount();
            if (success) {
              return ctx.succeededFuture(result);
            } else {
              return ctx.failedFuture(failure);
            }
          }
        };
      }
    };
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(provider);
    mgr.getConnection(ctx, TEST_KEY).onComplete(ar -> {
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
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Connection expected = new Connection();
    AtomicReference<Runnable> postCheck = new AtomicReference<>();
    boolean[] disposed = new boolean[1];
    EndpointProvider<Object, Connection> provider = new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, Runnable dispose) {
        return new Endpoint<Connection>(dispose) {
          @Override
          public Future<Connection> requestConnection(ContextInternal ctx, long timeout) {
            incRefCount();
            if (closeConnectionAfterCallback) {
              postCheck.set(() -> {
                assertFalse(disposed[0]);
                decRefCount();
                assertTrue(disposed[0]);
              });
              return ctx.succeededFuture(expected);
            } else {
              decRefCount();
              assertFalse(disposed[0]);
              postCheck.set(() -> {
                assertTrue(disposed[0]);
              });
              return ctx.succeededFuture(expected);
            }
          }

          @Override
          protected void dispose() {
            disposed[0] = true;
          }
        };
      }
    };
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(provider);
    mgr.getConnection(ctx, TEST_KEY).onComplete(onSuccess(conn -> {
      assertEquals(expected, conn);
      postCheck.get().run();
    }));
    waitUntil(() -> disposed[0]);
  }

  @Test
  public void testCloseManager() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Connection expected = new Connection();
    boolean[] disposed = new boolean[1];
    EndpointProvider<Object, Connection> provider = new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, Runnable dispose) {
        return new Endpoint<Connection>(dispose) {
          @Override
          public Future<Connection> requestConnection(ContextInternal ctx, long timeout) {
            incRefCount();
            return ctx.succeededFuture(expected);
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
    };
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(provider);
    CountDownLatch latch = new CountDownLatch(1);
    mgr.getConnection(ctx, TEST_KEY).onComplete(onSuccess(conn -> {
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
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Connection expected = new Connection();
    boolean[] disposed = new boolean[1];
    AtomicReference<Runnable> adder = new AtomicReference<>();
    EndpointProvider<Object, Connection> provider = (key, dispose) -> new Endpoint<Connection>(dispose) {
      @Override
      public Future<Connection> requestConnection(ContextInternal ctx1, long timeout) {
        adder.set(() -> {
          incRefCount();
        });
        return ctx1.promise();
      }
    };
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(provider);
    mgr.getConnection(ctx, TEST_KEY).onComplete(onSuccess(conn -> {
    }));
    waitUntil(() -> adder.get() != null);
    mgr.close();
    adder.get().run();
  }

  @Test
  public void testConcurrentDispose() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ConcurrentLinkedQueue<AtomicBoolean> disposals = new ConcurrentLinkedQueue<>();
    EndpointProvider<Object, Connection> provider = new EndpointProvider<Object, Connection>() {
      @Override
      public Endpoint<Connection> create(Object key, Runnable dispose) {
        AtomicBoolean disposed = new AtomicBoolean();
        disposals.add(disposed);
        return new Endpoint<Connection>(dispose) {
          @Override
          public Future<Connection> requestConnection(ContextInternal ctx, long timeout) {
            if (disposed.get()) {
              // Check we don't have reentrant demands once disposed
              fail();
              return ctx.promise();
            } else {
              Connection conn = new Connection();
              incRefCount();
              decRefCount();
              return ctx.succeededFuture(conn);
            }
          }

          @Override
          protected void dispose() {
            disposed.set(true);
          }
        };
      }
    };
    ConnectionManager<Object, Connection> mgr = new ConnectionManager<>(provider);
    int num = 100000;
    int concurrency = 4;
    CountDownLatch[] latches = new CountDownLatch[concurrency];
    for (int i = 0;i < concurrency;i++) {
      CountDownLatch cc = new CountDownLatch(num);
      latches[i] = cc;
      new Thread(() -> {
        for (int j = 0;j < num;j++) {
          mgr.getConnection(ctx, TEST_KEY).onComplete(onSuccess(conn -> {
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
