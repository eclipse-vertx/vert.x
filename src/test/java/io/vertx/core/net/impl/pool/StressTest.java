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

import io.vertx.core.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class StressTest extends VertxTestBase {

  class FakeConnectionPool implements PoolConnector<FakeConnection> {

    private ConnectionPool<FakeConnection> pool;

    FakeConnectionPool(int queueMaxSize, int poolMaxSize) {
      this.pool = ConnectionPool.pool(this, new int[]{ poolMaxSize }, queueMaxSize);
    }

    void getConnection(FakeWaiter waiter) {
      pool.acquire(waiter.context, 0, ar -> {
        if (ar.succeeded()) {
          waiter.handleConnection(ar.result());
        } else {
          waiter.handleFailure(ar.cause());
        }
      });
    }

    @Override
    public void connect(ContextInternal context, Listener listener, Handler<AsyncResult<ConnectResult<FakeConnection>>> handler) {
      int i = ThreadLocalRandom.current().nextInt(100);
      Promise<ConnectResult<FakeConnection>> promise = Promise.promise();
      Future<ConnectResult<FakeConnection>> future = promise.future();
      future.onComplete(handler);
      FakeConnection conn = new FakeConnection(context, listener, promise);
      if (i < 10) {
        conn.fail(new Exception("Could not connect"));
      } else {
        conn.connect();
      }
    }

    @Override
    public boolean isValid(FakeConnection connection) {
      return true;
    }
  }

  class FakeWaiter {

    protected final ContextInternal context;
    private Object result;

    FakeWaiter() {
      context = (ContextInternal) vertx.getOrCreateContext();
    }

    protected void onSuccess(FakeConnection conn) {

    }

    protected void onFailure() {

    }

    private synchronized void handleFailure(Throwable failure) {
      synchronized (this) {
        assertNull(result);
        result = failure;
      }
      onFailure();
    }

    private synchronized void handleConnection(Lease<FakeConnection> lease) {
      synchronized (this) {
        assertNull(result);
        result = lease;
      }
      onSuccess(lease.get());
    }

    void recycle() {
      Lease<?> conn = (Lease<?>) result;
      conn.recycle();
    }
  }

  private static class FakeConnection {

    private static final int DISCONNECTED = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED = 2;
    private static final int CLOSED = 3;

    private final ContextInternal context;
    private final PoolConnector.Listener listener;
    private final Promise<ConnectResult<FakeConnection>> future;

    private long concurrency = 1;
    private int status = DISCONNECTED;

    FakeConnection(ContextInternal context, PoolConnector.Listener listener, Promise<ConnectResult<FakeConnection>> future) {
      this.context = context;
      this.listener = listener;
      this.future = future;
    }

    synchronized void close() {
      if (status != CONNECTED) {
        throw new IllegalStateException();
      }
      status = CLOSED;
      listener.onRemove();
    }

    synchronized FakeConnection connect() {
      if (status != DISCONNECTED) {
        throw new IllegalStateException();
      }
      status = CONNECTING;
      context.nettyEventLoop().execute(() -> {
        synchronized (FakeConnection.this) {
          status = CONNECTED;
          future.complete(new ConnectResult<>(this, concurrency, 0));
        }
      });
      return this;
    }

    void fail(Throwable err) {
      context.nettyEventLoop().execute(() -> future.tryFail(err));
    }
  }

  @Test
  public void testStress() throws InterruptedException {
    int numActors = 16;
    int numConnections = 1000;

    FakeConnectionPool mgr = new FakeConnectionPool(-1, numActors);

    Thread[] actors = new Thread[numActors];
    for (int i = 0; i < numActors; i++) {
      actors[i] = new Thread(() -> {
        CountDownLatch latch = new CountDownLatch(numConnections);
        for (int i1 = 0; i1 < numConnections; i1++) {
          mgr.getConnection(new FakeWaiter() {
            @Override
            protected void onFailure() {
              latch.countDown();
            }
            @Override
            protected void onSuccess(FakeConnection conn) {
              int action = ThreadLocalRandom.current().nextInt(100);
              if (action < -1) {
                recycle();
                latch.countDown();
              } else {
                vertx.setTimer(10, id -> {
                  if (action < 15) {
                    conn.close();
                  } else {
                    recycle();
                  }
                  latch.countDown();
                });
              }
            }
          });
        }
        try {
          latch.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }, "actor-" + i);
      actors[i].start();
    }

    for (int i = 0; i < actors.length; i++) {
      try {
        actors[i].join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    // This is synchronous
    CountDownLatch latch = new CountDownLatch(1);
    mgr.pool.close(ar -> {
      if (ar.succeeded()) {
/*
        List<Future> list = (List) ar.result();
        CompositeFuture.all(list).onSuccess(c -> {
          for (int i = 0;i < c.size();i++) {
            Object o = c.resultAt(i);
          }
        });
*/
      }
      latch.countDown();
    });
    awaitLatch(latch);

    // Check state at the end
//    mgr.pool.checkInvariants();
    assertEquals(0, mgr.pool.requests());
    assertEquals(0, mgr.pool.size());
    assertEquals(0, mgr.pool.capacity());
    assertEquals(0, mgr.pool.waiters());

  }
}
