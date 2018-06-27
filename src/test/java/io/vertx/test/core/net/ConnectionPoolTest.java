/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core.net;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.impl.pool.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ConnectionPoolTest extends VertxTestBase {

  class FakeConnectionManager {

    private final ConnectionProvider<FakeConnection> connector;
    private final int queueMaxSize;
    private final int maxPoolSize;
    private Pool<FakeConnection> pool;
    private Set<FakeConnection> active = new HashSet<>();
    private boolean closed = true;
    private int seq;
    private final boolean fifo;

    FakeConnectionManager(int queueMaxSize, int maxPoolSize, ConnectionProvider<FakeConnection> connector) {
      this(queueMaxSize, maxPoolSize, connector, false);
    }

    FakeConnectionManager(int queueMaxSize, int maxPoolSize, ConnectionProvider<FakeConnection> connector, boolean fifo) {
      this.queueMaxSize = queueMaxSize;
      this.maxPoolSize = maxPoolSize;
      this.connector = connector;
      this.fifo = fifo;
    }

    synchronized int sequence() {
      return seq;
    }

    synchronized boolean closed() {
      return closed;
    }

    synchronized boolean contains(FakeConnection conn) {
      return active.contains(conn);
    }

    synchronized int size() {
      return active.size();
    }

    int removeExpired(long timestamp) {
      return pool.closeIdle(timestamp);
    }

    synchronized Pool<FakeConnection> pool() {
      return pool;
    }

    void getConnection(FakeWaiter waiter) {
      synchronized (this) {
        if (closed) {
          seq++;
          closed = false;
          pool = new Pool<>(
            connector,
            queueMaxSize,
            1,
            maxPoolSize,
            v -> {
              synchronized (FakeConnectionManager.this) {
                closed = true;
              }
            }, (channel, conn) -> {
            synchronized (FakeConnectionManager.this) {
              active.add(conn);
            }
          }, (channel, conn) -> {
            synchronized (FakeConnectionManager.this) {
              active.remove(conn);
            }
          }, fifo
          );
        }
      }
      pool.getConnection(waiter.context, waiter.handler);
    }
  }

  @Test
  public void testConnectSuccess() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 4, connector);
    AtomicReference<Boolean> handleLock = new AtomicReference<>();
    FakeWaiter waiter = new FakeWaiter() {
      @Override
      public synchronized void handleConnection(FakeConnection conn) {
        assertNull(Vertx.currentContext());
        assertSame(conn.context, context);
        Pool<FakeConnection> pool = mgr.pool();
        handleLock.set(Thread.holdsLock(pool));
        super.handleConnection(conn);
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter::isComplete);
    assertEquals(Boolean.FALSE, handleLock.get());
    waiter.assertSuccess(conn);
    waiter.recycle();
    assertEquals(0, mgr.size());
    assertTrue(mgr.closed());
  }

  @Test
  public void testConnectFailure() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 4, connector);
    AtomicReference<Boolean> holdsLock = new AtomicReference<>();
    FakeWaiter waiter = new FakeWaiter() {
      @Override
      public synchronized void handleFailure(Throwable failure) {
        assertNull(Vertx.currentContext());
        Pool<FakeConnection> pool = mgr.pool();
        holdsLock.set(Thread.holdsLock(pool));
        super.handleFailure(failure);
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    Throwable failure = new Throwable();
    conn.fail(failure);
    assertWaitUntil(waiter::isComplete);
    assertEquals(Boolean.FALSE, holdsLock.get());
    waiter.assertFailure(failure);
  }

  @Test
  public void testConnectPoolEmptyWaiterCancelledAfterConnectRequest() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 3, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    waiter.cancel();
    conn.connect();
    assertWaitUntil(waiter::isComplete);
    assertFalse(waiter.isSuccess());
    assertFalse(waiter.isFailure());
    assertFalse(mgr.contains(conn));
  }

  @Test
  public void testConnectionFailure() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 3, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    Exception expected = new Exception();
    conn.fail(expected);
    assertWaitUntil(waiter::isComplete);
    waiter.assertFailure(expected);
    assertTrue(waiter.isFailure());
    assertWaitUntil(mgr::closed);
  }

  @Test
  public void testSynchronousConnectionFailure() {
    Throwable cause = new Throwable();
    ConnectionProvider<FakeConnection> connector = new FakeConnectionProviderBase() {
      @Override
      public void connect(ConnectionListener<FakeConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<FakeConnection>>> handler) {
        handler.handle(Future.failedFuture(cause));
      }
    };
    FakeConnectionManager mgr = new FakeConnectionManager(3, 3, connector);
    for (int i = 0;i < 4;i++) {
      FakeWaiter waiter = new FakeWaiter();
      mgr.getConnection(waiter);
      waitUntil(waiter::isFailure);
      waiter.assertFailure(cause);
      assertEquals(0, mgr.pool().weight());
    }
    assertTrue(mgr.closed());
  }

  @Test
  public void testRecycleConnection() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    connector.assertRequests(0);
    waiter1.recycle();
    assertWaitUntil(waiter2::isComplete);
    waiter2.assertSuccess(conn);
  }

  @Test
  public void testRecycleDiscardedConnection() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    conn.close();
    waiter1.recycle();
    assertWaitUntil(() -> connector.requests() == 1);
    assertFalse(mgr.closed());
    FakeConnection conn2 = connector.assertRequest();
    conn2.connect();
    assertWaitUntil(waiter2::isSuccess);
  }

  /*
  @Test
  public void testWaiterThrowsException() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    Exception failure = new Exception();
    FakeWaiter waiter = new FakeWaiter() {
      @Override
      public synchronized boolean handleConnection(ContextInternal ctx, FakeConnection conn) throws Exception {
        throw failure;
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertEquals(0, mgr.size());
  }
  */

  @Test
  public void testEndpointLifecycle() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isSuccess);
    conn.close();
    assertWaitUntil(mgr::closed);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    assertEquals(2, mgr.sequence());
  }

  @Test
  public void testDontCloseEndpointWithInflightRequest() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 2, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    conn.close();
    assertWaitUntil(() -> !mgr.contains(conn));
    assertFalse(mgr.closed());
  }

  @Test
  public void testInitialConcurrency() {
    int n = 10;
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 1, connector);
    List<FakeWaiter> waiters = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      FakeWaiter waiter = new FakeWaiter();
      mgr.getConnection(waiter);
      waiters.add(waiter);
    }
    FakeConnection conn = connector.assertRequest();
    conn.concurrency(n).connect();
    waiters.forEach(waiter -> {
      assertWaitUntil(waiter::isSuccess);
    });
    waiters.forEach(FakeWaiter::recycle);
  }

  @Test
  public void testInitialNoConcurrency() {
    int n = 10;
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 1, connector);
    List<FakeWaiter> waiters = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      FakeWaiter waiter = new FakeWaiter();
      mgr.getConnection(waiter);
      waiters.add(waiter);
    }
    FakeConnection conn = connector.assertRequest();
    conn.concurrency(0).connect().awaitConnected();
    conn.concurrency(n - 1);
    assertWaitUntil(() -> waiters.stream().filter(FakeWaiter::isSuccess).count() == n - 1);
    waiters.stream().filter(FakeWaiter::isSuccess).findFirst().get().recycle();
    waiters.forEach(waiter -> {
      assertWaitUntil(waiter::isSuccess);
    });
  }

  @Test
  public void testRecycleWithoutDispose() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    assertFalse(conn.isActive());
    conn.connect();
    assertWaitUntil(waiter1::isSuccess);
    assertTrue(conn.isActive());
    conn.recycle(false);
    assertFalse(conn.isActive());
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    assertWaitUntil(waiter2::isSuccess);
    assertTrue(conn.isActive());
    waiter2.assertSuccess(conn);
    conn.recycle(true);
    assertTrue(conn.isActive());
    assertEquals(0, mgr.size());
  }

  @Test
  public void testRecycleFIFO() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 2, connector, true);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection firstInConnection = connector.assertRequest();
    firstInConnection.connect();
    assertWaitUntil(waiter1::isSuccess);

    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    FakeConnection lastInConnection = connector.assertRequest();
    lastInConnection.connect();
    assertWaitUntil(waiter2::isSuccess);
    waiter2.assertSuccess(lastInConnection);
    firstInConnection.recycle(false);
    lastInConnection.recycle(false);
    assertEquals(2, mgr.size());

    FakeWaiter waiter3 = new FakeWaiter();
    mgr.getConnection(waiter3);
    assertWaitUntil(waiter3::isSuccess);
    waiter3.assertSuccess(firstInConnection);
  }

  @Test
  public void testRecycleLIFO() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 2, connector, false);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection firstInConnection = connector.assertRequest();
    firstInConnection.connect();
    assertWaitUntil(waiter1::isSuccess);

    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    FakeConnection lastInConnection = connector.assertRequest();
    lastInConnection.connect();
    assertWaitUntil(waiter2::isSuccess);
    waiter2.assertSuccess(lastInConnection);
    firstInConnection.recycle(false);
    lastInConnection.recycle(false);
    assertEquals(2, mgr.size());

    FakeWaiter waiter3 = new FakeWaiter();
    mgr.getConnection(waiter3);
    assertWaitUntil(waiter3::isSuccess);
    waiter3.assertSuccess(lastInConnection);
  }

  @Test
  public void testDiscardWaiterWhenFull() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(2, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    FakeWaiter waiter3 = new FakeWaiter();
    mgr.getConnection(waiter3);
    FakeWaiter waiter4 = new FakeWaiter();
    mgr.getConnection(waiter4);
    assertWaitUntil(waiter4::isFailure); // Full
  }

  /*
  @Test
  public void testDiscardConnectionDuringInit() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(2, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter() {
      @Override
      public synchronized void initConnection(ContextInternal ctx, FakeConnection conn) {
        super.initConnection(ctx, conn);
        conn.close(); // Close during init
      }
    };
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(() -> connector.requests() == 1); // Connection close during init - reattempt to connect
    assertFalse(mgr.closed());
  }
  */

  @Test
  public void testDiscardExpiredConnections() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(2, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isSuccess);
    conn.recycle(2L);
    assertEquals(1, mgr.size());
    assertEquals(0, mgr.removeExpired(1L));
    assertEquals(1, mgr.size());
    assertEquals(1, mgr.removeExpired(2L));
    assertEquals(0, mgr.size());
  }

  @Test
  public void testStress() {
    int numActors = 16;
    int numConnections = 1000;

    FakeConnectionProvider connector = new FakeConnectionProvider() {
      @Override
      public void connect(ConnectionListener<FakeConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<FakeConnection>>> handler) {
        int i = ThreadLocalRandom.current().nextInt(100);
        FakeConnection conn = new FakeConnection(context, listener, Future.<ConnectResult<FakeConnection>>future().setHandler(handler));
        if (i < 10) {
          conn.fail(new Exception("Could not connect"));
        } else {
          conn.connect();
        }
      }
    };
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 16, connector);

    Thread[] actors = new Thread[numActors];
    for (int i = 0; i < numActors; i++) {
      actors[i] = new Thread(() -> {
        CountDownLatch latch = new CountDownLatch(numConnections);
        for (int i1 = 0; i1 < numConnections; i1++) {
          mgr.getConnection(new FakeWaiter() {
            @Override
            public void handleFailure(Throwable failure) {
              latch.countDown();
            }

            @Override
            public void handleConnection(FakeConnection conn) {
              int action = ThreadLocalRandom.current().nextInt(100);
              if (action < -1) {
                latch.countDown();
                conn.listener.onRecycle(0L);
              } /* else if (i < 30) {
                latch.countDown();
                throw new Exception();
              } */ else {
                vertx.setTimer(10, id -> {
                  if (action < 15) {
                    conn.close();
                  } else {
                    conn.recycle();
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
      });
      actors[i].start();
    }

    for (int i = 0; i < actors.length; i++) {
      try {
        actors[i].join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    assertWaitUntil(() -> mgr.closed());

    // Check state at the end
    assertEquals(0, mgr.size());
    assertEquals(0, mgr.pool.waitersCount());
    assertEquals(0, mgr.pool.waitersInQueue());
    assertEquals(0, mgr.pool.weight());
    assertEquals(0, mgr.pool.capacity());
  }

  class FakeWaiter {

    protected final ContextInternal context;
    private boolean cancelled;
    private boolean completed;
    private Object result;
    private final Handler<AsyncResult<FakeConnection>> handler;

    FakeWaiter() {
      context = (ContextInternal) vertx.getOrCreateContext();
      handler = ar -> {
        if (ar.succeeded()) {
          handleConnection(ar.result());
        } else {
          handleFailure(ar.cause());
        }
      };
    }

    synchronized boolean cancel() {
      if (completed) {
        return false;
      } else {
        cancelled = true;
        return true;
      }
    }

    synchronized void assertSuccess(FakeConnection conn) {
      assertSame(conn, result);
    }

    synchronized void assertFailure(Throwable failure) {
      assertSame(failure, result);
    }

    synchronized boolean isComplete() {
      return completed;
    }

    synchronized boolean isSuccess() {
      return completed && result instanceof FakeConnection;
    }

    synchronized boolean isFailure() {
      return completed && result instanceof Throwable;
    }

    public synchronized void handleFailure(Throwable failure) {
      assertFalse(completed);
      completed = true;
      result = failure;
    }

    public synchronized void handleConnection(FakeConnection conn) {
      assertFalse(completed);
      completed = true;
      if (cancelled) {
        conn.listener.onRecycle(0L);
      } else {
        synchronized (conn) {
          conn.inflight++;
        }
        result = conn;
      }
    }

    long recycle() {
      FakeConnection conn = (FakeConnection) result;
      return conn.recycle();
    }
  }

  /*
    class FakeConnnectionPool implements ConnectionPool<FakeConnection>, Function<SocketAddress, ConnectionPool<FakeConnection>> {

      private final SocketAddress address = SocketAddress.inetSocketAddress(8080, "localhost");
      private final int maxSize;
      private final ArrayDeque<FakeConnection> available = new ArrayDeque<>();
      private final Set<FakeConnection> all = new HashSet<>();
      private boolean closed = true;
      private int sequence;

      FakeConnnectionPool(int maxSize) {
        this.maxSize = maxSize;
      }

      @Override
      public Deque<FakeConnection> available() {
        return available;
      }

      @Override
      public Set<FakeConnection> all() {
        return all;
      }

      synchronized int size() {
        return available.size();
      }

      synchronized boolean contains(FakeConnection conn) {
        Deque<ConnectionHolder<FakeConnection>> a = (Deque<ConnectionHolder<FakeConnection>>)(Deque) available;
        for (ConnectionHolder<FakeConnection> b : a) {
          if (b.connection() == conn) {
            return true;
          }
        }
        return false;
      }

      synchronized int sequence() {
        return sequence;
      }

      @Override
      public synchronized FakeConnnectionPool apply(SocketAddress socketAddress) {
        if (!socketAddress.equals(address)) {
          throw new AssertionError();
        }
        if (!closed) {
          throw new AssertionError();
        }
        closed = false;
        sequence++;
        return this;
      }

      @Override
      public synchronized int maxSize() {
        if (closed) {
          throw new AssertionError();
        }
        return maxSize;
      }

      @Override
      public synchronized boolean canBorrow(int connCount) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized FakeConnection pollConnection() {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized boolean canCreateConnection(int connCount) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized boolean initConnection(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized void recycleConnection(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized void evictConnection(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized boolean isValid(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized ContextImpl getContext(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      public synchronized void close() {
        if (closed) {
          throw new AssertionError();
        }
        closed = true;
        available.clear();
        all.clear();
      }

      synchronized boolean isClosed() {
        return closed;
      }
    }
  */
  class FakeConnection {

    private static final int DISCONNECTED = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED = 2;
    private static final int CLOSED = 3;

    private final ContextInternal context;
    private final ConnectionListener<FakeConnection> listener;
    private final Future<ConnectResult<FakeConnection>> future;
    private final Channel channel = new EmbeddedChannel();

    private long inflight;
    private long concurrency = 1;
    private int status = DISCONNECTED;
    private boolean active;

    FakeConnection(ContextInternal context, ConnectionListener<FakeConnection> listener, Future<ConnectResult<FakeConnection>> future) {
      this.context = context;
      this.listener = listener;
      this.future = future;
    }

    synchronized boolean isActive() {
      return active;
    }

    synchronized void close() {
      if (status != CONNECTED) {
        throw new IllegalStateException();
      }
      status = CLOSED;
      listener.onDiscard();
    }

    synchronized long recycle(boolean dispose) {
      return recycle(dispose ? 0L : Long.MAX_VALUE);
    }

    synchronized long recycle() {
      return recycle(true);
    }

    synchronized long recycle(long timestamp) {
      inflight -= 1;
      listener.onRecycle(timestamp);
      return inflight;
    }

    synchronized void activate() {
      assertFalse(active);
      active = true;
    }

    synchronized void deactivate() {
      assertTrue(active);
      active = false;
    }

    synchronized FakeConnection concurrency(long value) {
      if (value < 0) {
        throw new IllegalArgumentException("Invalid concurrency");
      }
      if (status == CONNECTED) {
        if (concurrency != value) {
          concurrency = value;
          listener.onConcurrencyChange(value);
        }
      } else {
        concurrency = value;
      }
      return this;
    }

    FakeConnection awaitConnected() {
      waitUntil(() -> {
        synchronized (FakeConnection.this) {
          return status == CONNECTED;
        }
      });
      return this;
    }

    synchronized FakeConnection connect() {
      if (status != DISCONNECTED) {
        throw new IllegalStateException();
      }
      status = CONNECTING;
      context.nettyEventLoop().execute(() -> {
        synchronized (FakeConnection.this) {
          status = CONNECTED;
          future.complete(new ConnectResult<>(this, concurrency, channel, context, 1));
        }
      });
      return this;
    }

    void fail(Throwable err) {
      context.nettyEventLoop().execute(() -> future.tryFail(err));
    }
  }

  abstract class FakeConnectionProviderBase implements ConnectionProvider<FakeConnection> {

    @Override
    public void close(FakeConnection conn) {
      conn.listener.onDiscard();
    }

    @Override
    public void activate(FakeConnection conn) {
      conn.activate();
    }

    @Override
    public void deactivate(FakeConnection conn) {
      conn.deactivate();
    }
  }

  class FakeConnectionProvider extends FakeConnectionProviderBase {

    private final Deque<FakeConnection> pendingRequests = new ConcurrentLinkedDeque<>();

    void assertRequests(int expectedSize) {
      assertEquals(expectedSize, pendingRequests.size());
    }

    int requests() {
      return pendingRequests.size();
    }

    FakeConnection assertRequest() {
      waitUntil(() -> pendingRequests.size() > 0);
      FakeConnection request = pendingRequests.poll();
      assertNotNull(request);
      return request;
    }

    @Override
    public void connect(ConnectionListener<FakeConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<FakeConnection>>> handler) {
      pendingRequests.add(new FakeConnection(context, listener, Future.<ConnectResult<FakeConnection>>future().setHandler(handler)));
    }
  }
}
