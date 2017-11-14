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
import io.vertx.core.http.impl.pool.*;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public class ConnectionManagerTest extends VertxTestBase {

  private static final SocketAddress TEST_ADDRESS = SocketAddress.inetSocketAddress(8080, "localhost");

  class FakeConnectionManager {

    private final FakeConnectionProvider connector;
    private final int queueMaxSize;
    private final int maxPoolSize;
    private Pool<FakeConnection> delegate;
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

    void getConnection(Waiter<FakeConnection> waiter) {
      synchronized (this) {
        if (closed) {
          seq++;
          closed = false;
          delegate = new Pool<>(
            connector,
            null,
            queueMaxSize,
            "localhost",
            "localhost",
            8080,
            false,
            maxPoolSize,
            v -> {
              synchronized (FakeConnectionManager.this) {
                closed = true;
              }
            }, conn -> {
            synchronized (FakeConnectionManager.this) {
              active.add(conn.connection());
              size++;
            }
          }, conn -> {
            synchronized (FakeConnectionManager.this) {
              size--;
              active.remove(conn.connection());
            }
          }
          );
        }
      }
      delegate.getConnection(waiter);
    }
  }

  @Test
  public void testConnectPoolEmpty() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 4, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
    conn.connect();
    assertWaitUntil(waiter::isComplete);
    waiter.assertInitialized(conn);
    waiter.assertSuccess(conn);
  }

  @Test
  public void testConnectPoolEmptyWaiterCancelledAfterConnectRequest() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 3, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
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
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
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
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
    conn.connect();
    assertWaitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    connector.assertRequests(TEST_ADDRESS, 0);
    waiter1.recycle();
    assertWaitUntil(waiter2::isComplete);
    waiter2.assertSuccess(conn);
  }

  /*
    @Test
    public void testRecycleClosedConnection() {
      FakeConnnectionPool pool = new FakeConnnectionPool(1);
      FakeConnectionProvider provider = new FakeConnectionProvider();
      ConnectionManager<FakeConnection> mgr = new ConnectionManager<>(null, provider, pool, 3);
      FakeWaiter waiter1 = new FakeWaiter();
      mgr.getConnection("localhost", false, 8080, "localhost", waiter1);
      FakeConnection conn = provider.assertRequest(TEST_ADDRESS);
      conn.connect();
      assertWaitUntil(waiter1::isComplete);
      FakeWaiter waiter2 = new FakeWaiter();
      mgr.getConnection("localhost", false, 8080, "localhost", waiter2);
      provider.assertRequests(TEST_ADDRESS, 0);
      conn.close();
      conn.recycle();
  //    assertWaitUntil(waiter2::isComplete);
  //    waiter2.assertSuccess(conn);
    }
  */
  @Test
  public void testRecycleInvalidConnection() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
    conn.connect();
    waitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    conn.invalidate();
    waiter1.recycle();
    waitUntil(() -> connector.requests(TEST_ADDRESS) == 1);
    assertFalse(mgr.closed());
    FakeConnection conn2 = connector.assertRequest(TEST_ADDRESS);
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
      public synchronized boolean handleConnection(FakeConnection conn) throws Exception {
        throw failure;
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
    conn.connect();
    assertEquals(0, mgr.size());
  }

  @Test
  public void testEndpointLifecycle() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
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
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
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
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
    conn.concurrency(n).connect();
    waiters.forEach(waiter -> {
      waitUntil(waiter::isSuccess);
    });
    waiters.forEach(FakeWaiter::recycle);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    waitUntil(waiter::isComplete);
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
    FakeConnection conn = connector.assertRequest(TEST_ADDRESS);
    conn.concurrency(0).connect().awaitConnected();
    conn.concurrency(n - 1);
    waitUntil(() -> waiters.stream().filter(FakeWaiter::isSuccess).count() == n - 1);
    waiters.stream().filter(FakeWaiter::isSuccess).findFirst().get().recycle();
    waiters.forEach(waiter -> {
      waitUntil(waiter::isSuccess);
    });
  }

  @Test
  public void testStress() {
    int numActors = 16;
    int numConnections = 1000;

    FakeConnectionProvider connector = new FakeConnectionProvider() {
      @Override
      public long connect(ConnectionListener<FakeConnection> listener, Object endpointMetric, ContextImpl context, String peerHost, boolean ssl, String host, int port) {
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
            public void initConnection(FakeConnection conn) {
            }

            @Override
            public boolean handleConnection(FakeConnection conn) throws Exception {
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
        System.out.println("DONE");
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
    public synchronized void initConnection(FakeConnection conn) {
      assertNull(init);
      init = conn;
    }

    @Override
    public synchronized boolean handleConnection(FakeConnection conn) throws Exception {
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
    private boolean valid = true;

    FakeConnection(ContextImpl context, ConnectionListener<FakeConnection> listener) {
      this.context = context;
      this.listener = listener;
    }

    synchronized FakeConnection invalidate() {
      valid = false;
      return this;
    }

    synchronized void close() {
      if (status != CONNECTED) {
        throw new IllegalStateException();
      }
      status = CLOSED;
      listener.onClose(this);
    }

    synchronized long recycle() {
      long i = inflight--;
      listener.onRecycle(this);
      return i;
    }

    synchronized FakeConnection concurrency(long value) {
      if (value < 0) {
        throw new IllegalArgumentException("Invalid concurrency");
      }
      if (status == CONNECTED) {
        if (concurrency != value) {
          concurrency = value;
          listener.onConcurrencyChange(this, value);
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
          listener.onConnectSuccess(this, concurrency, channel, context, 1, 1, Long.MAX_VALUE);
          status = CONNECTED;
        }
      });
      return this;
    }

    void fail(Throwable err) {
      context.nettyEventLoop().execute(() -> listener.onConnectFailure(err, 1));
    }

    synchronized boolean isValid() {
      return valid;
    }
  }

  class FakeConnectionProvider implements ConnectionProvider<FakeConnection> {

    private final Map<SocketAddress, ArrayDeque<FakeConnection>> requestMap = new HashMap<>();

    void assertRequests(SocketAddress address, int expectedSize) {
      ArrayDeque<FakeConnection> requests = requestMap.get(address);
      if (expectedSize == 0) {
        assertTrue(requests == null || requests.size() == 0);
      } else {
        assertNotNull(requests);
        assertEquals(expectedSize, requests.size());
      }
    }

    @Override
    public boolean isValid(FakeConnection conn) {
      return conn.isValid();
    }

    int requests(SocketAddress address) {
      ArrayDeque<FakeConnection> requests = requestMap.get(address);
      return requests == null ? 0 : requests.size();
    }

    FakeConnection assertRequest(SocketAddress address) {
      ArrayDeque<FakeConnection> requests = requestMap.get(address);
      assertNotNull(requests);
      assertTrue(requests.size() > 0);
      FakeConnection request = requests.poll();
      assertNotNull(request);
      return request;
    }

    @Override
    public long connect(ConnectionListener<FakeConnection> listener,
                        Object endpointMetric,
                        ContextImpl context,
                        String peerHost,
                        boolean ssl,
                        String host,
                        int port) {
      ArrayDeque<FakeConnection> list = requestMap.computeIfAbsent(SocketAddress.inetSocketAddress(port, host), address -> new ArrayDeque<>());
      list.add(new FakeConnection(context, listener));
      return 1;
    }

    @Override
    public void close(FakeConnection conn) {

    }
  }
}
