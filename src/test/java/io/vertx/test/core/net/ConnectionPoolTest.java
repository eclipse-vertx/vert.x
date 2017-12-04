/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.test.core.net;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.vertx.core.Vertx;
import io.vertx.core.http.impl.pool.*;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ConnectionPoolTest extends VertxTestBase {

  private static final SocketAddress TEST_ADDRESS = SocketAddress.inetSocketAddress(8080, "localhost");

  class FakeConnectionManager {

    private final FakeConnectionProvider connector;
    private final int queueMaxSize;
    private final int maxPoolSize;
    private Pool<FakeConnection> pool;
    private int size;
    private Set<FakeConnection> active = new HashSet<>();
    private boolean closed = true;
    private int seq;

    FakeConnectionManager(int queueMaxSize, int maxPoolSize, FakeConnectionProvider connector) {
      this.queueMaxSize = queueMaxSize;
      this.maxPoolSize = maxPoolSize;
      this.connector = connector;
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
      return size;
    }

    synchronized Pool<FakeConnection> pool() {
      return pool;
    }

    void getConnection(Waiter<FakeConnection> waiter) {
      synchronized (this) {
        if (closed) {
          seq++;
          closed = false;
          pool = new Pool<>(
            connector,
            queueMaxSize,
            maxPoolSize,
            v -> {
              synchronized (FakeConnectionManager.this) {
                closed = true;
              }
            }, (channel, conn) -> {
            synchronized (FakeConnectionManager.this) {
              active.add(conn);
              size++;
            }
          }, (channel, conn) -> {
            synchronized (FakeConnectionManager.this) {
              size--;
              active.remove(conn);
            }
          }
          );
        }
      }
      pool.getConnection(waiter);
    }
  }

  @Test
  public void testConnectSuccess() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 4, connector);
    AtomicReference<Boolean> initLock = new AtomicReference<>();
    AtomicReference<Boolean> handleLock = new AtomicReference<>();
    FakeWaiter waiter = new FakeWaiter() {
      @Override
      public synchronized void initConnection(ContextInternal ctx, FakeConnection conn) {
        assertNull(Vertx.currentContext());
        assertSame(ctx, context);
        Pool<FakeConnection> pool = mgr.pool();
        initLock.set(Thread.holdsLock(pool));
        super.initConnection(ctx, conn);
      }
      @Override
      public synchronized boolean handleConnection(ContextInternal ctx, FakeConnection conn) throws Exception {
        assertNull(Vertx.currentContext());
        assertSame(ctx, context);
        Pool<FakeConnection> pool = mgr.pool();
        handleLock.set(Thread.holdsLock(pool));
        return super.handleConnection(ctx, conn);
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter::isComplete);
    assertEquals(Boolean.FALSE, handleLock.get());
    assertEquals(Boolean.FALSE, initLock.get());
    waiter.assertInitialized(conn);
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
      public synchronized void handleFailure(ContextInternal ctx, Throwable failure) {
        assertNull(Vertx.currentContext());
        assertSame(ctx, context);
        Pool<FakeConnection> pool = mgr.pool();
        holdsLock.set(Thread.holdsLock(pool));
        super.handleFailure(ctx, failure);
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    Throwable failure = new Throwable();
    conn.fail(failure);
    assertWaitUntil(waiter::isComplete);
    assertEquals(Boolean.FALSE, holdsLock.get());
    waiter.assertNotInitialized();
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
    waitUntil(() -> mgr.size() == 1);
    waiter.assertInitialized(conn);
    assertTrue(waiter.isComplete());
    assertFalse(waiter.isSuccess());
    assertFalse(waiter.isFailure());
    assertTrue(mgr.contains(conn));
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
    waitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    conn.close();
    waiter1.recycle();
    waitUntil(() -> connector.requests(TEST_ADDRESS) == 1);
    assertFalse(mgr.closed());
    FakeConnection conn2 = connector.assertRequest();
    conn2.connect();
    waitUntil(waiter2::isSuccess);
  }

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

  @Test
  public void testEndpointLifecycle() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    waitUntil(waiter1::isSuccess);
    conn.close();
    waitUntil(mgr::closed);
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
    waitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    conn.close();
    waitUntil(() -> !mgr.contains(conn));
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
      waitUntil(waiter::isSuccess);
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
    waitUntil(() -> waiters.stream().filter(FakeWaiter::isSuccess).count() == n - 1);
    waiters.stream().filter(FakeWaiter::isSuccess).findFirst().get().recycle();
    waiters.forEach(waiter -> {
      waitUntil(waiter::isSuccess);
    });
  }

  @Test
  public void testRecycleWithoutDispose() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    waitUntil(waiter1::isSuccess);
    conn.recycle(false);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    waitUntil(waiter1::isSuccess);
    waiter2.assertSuccess(conn);
    conn.recycle(true);
    assertEquals(0, mgr.size());
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

  @Test
  public void testStress() {
    int numActors = 16;
    int numConnections = 1000;

    FakeConnectionProvider connector = new FakeConnectionProvider() {
      @Override
      public long connect(ConnectionListener<FakeConnection> listener, ContextImpl context) {
        int i = ThreadLocalRandom.current().nextInt(100);
        FakeConnection conn = new FakeConnection(context, listener);
        if (i < 10) {
          conn.fail(new Exception("Could not connect"));
        } else {
          conn.connect();
        }
        return 1;
      }
    };
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 16, connector);

    Thread[] actors = new Thread[numActors];
    for (int i = 0; i < numActors; i++) {
      actors[i] = new Thread(() -> {
        CountDownLatch latch = new CountDownLatch(numConnections);
        for (int i1 = 0; i1 < numConnections; i1++) {
          mgr.getConnection(new Waiter<FakeConnection>((ContextImpl) vertx.getOrCreateContext()) {
            @Override
            public void handleFailure(ContextInternal ctx, Throwable failure) {
              latch.countDown();
            }

            @Override
            public void initConnection(ContextInternal ctx, FakeConnection conn) {
            }

            @Override
            public boolean handleConnection(ContextInternal ctx, FakeConnection conn) throws Exception {
              int action = ThreadLocalRandom.current().nextInt(100);
              if (action < -1) {
                latch.countDown();
                return false;
              } /* else if (i < 30) {
                latch.countDown();
                throw new Exception();
              } */ else {
                vertx.setTimer(10, id -> {
                  latch.countDown();
                  if (action < 15) {
                    conn.close();
                  } else {
                    conn.recycle();
                  }
                });
                return true;
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
  }

  class FakeWaiter extends Waiter<FakeConnection> {

    private FakeConnection init;
    private boolean cancelled;
    private boolean completed;
    private Object result;

    FakeWaiter() {
      super((ContextImpl) vertx.getOrCreateContext());
    }

    synchronized boolean cancel() {
      if (completed) {
        return false;
      } else {
        cancelled = true;
        return true;
      }
    }

    synchronized void assertInitialized(FakeConnection conn) {
      assertSame(conn, init);
    }

    synchronized void assertNotInitialized() {
      assertSame(null, init);
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

    @Override
    public synchronized void handleFailure(ContextInternal ctx, Throwable failure) {
      assertFalse(completed);
      completed = true;
      result = failure;
    }

    @Override
    public synchronized void initConnection(ContextInternal ctx, FakeConnection conn) {
      assertNull(init);
      assertNotNull(conn);
      init = conn;
    }

    @Override
    public synchronized boolean handleConnection(ContextInternal ctx, FakeConnection conn) throws Exception {
      assertFalse(completed);
      completed = true;
      if (cancelled) {
        return false;
      } else {
        synchronized (conn) {
          conn.inflight++;
        }
        result = conn;
        return true;
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

    private final ContextImpl context;
    private final ConnectionListener<FakeConnection> listener;
    private final Channel channel = new EmbeddedChannel();

    private long inflight;
    private long concurrency = 1;
    private int status = DISCONNECTED;

    FakeConnection(ContextImpl context, ConnectionListener<FakeConnection> listener) {
      this.context = context;
      this.listener = listener;
    }

    synchronized void close() {
      if (status != CONNECTED) {
        throw new IllegalStateException();
      }
      status = CLOSED;
      listener.onDiscard();
    }

    synchronized long recycle(boolean dispose) {
      return recycle(1, dispose);
    }

    synchronized long recycle() {
      return recycle(true);
    }

    synchronized long recycle(int capacity, boolean dispose) {
      inflight -= capacity;
      listener.onRecycle(capacity, dispose);
      return inflight;
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
          listener.onConnectSuccess(this, concurrency, channel, context, 1, 1);
          status = CONNECTED;
        }
      });
      return this;
    }

    void fail(Throwable err) {
      context.nettyEventLoop().execute(() -> listener.onConnectFailure(context, err, 1));
    }
  }

  class FakeConnectionProvider implements ConnectionProvider<FakeConnection> {

    private final ArrayDeque<FakeConnection> pendingRequests = new ArrayDeque<>();

    void assertRequests(int expectedSize) {
      assertEquals(expectedSize, pendingRequests.size());
    }

    int requests(SocketAddress address) {
      return pendingRequests.size();
    }

    FakeConnection assertRequest() {
      assertNotNull(pendingRequests);
      assertTrue(pendingRequests.size() > 0);
      FakeConnection request = pendingRequests.poll();
      assertNotNull(request);
      return request;
    }

    @Override
    public long connect(ConnectionListener<FakeConnection> listener, ContextImpl context) {
      pendingRequests.add(new FakeConnection(context, listener));
      return 1;
    }

    @Override
    public void close(FakeConnection conn) {
      conn.listener.onDiscard();
    }
  }
}
