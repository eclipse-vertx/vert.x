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
package io.vertx.tests.pool;

import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.PoolConnector;
import io.vertx.core.internal.pool.PoolWaiter;
import io.vertx.core.internal.pool.DefaultConnectionPool;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultConnectionPoolTest extends ConnectionPoolTest {

  @Test
  public void testStaleConnectionEviction() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = pool(mgr, new int[] { 2 });
    Connection conn1 = new Connection();
    Connection conn2 = new Connection();
    CountDownLatch latch = new CountDownLatch(2);
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      lease.recycle();
      latch.countDown();
    }));
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      lease.recycle();
      latch.countDown();
    }));
    mgr.assertRequest().connect(conn1, 0);
    mgr.assertRequest().connect(conn2, 0);
    TestUtils.awaitLatch(latch);
    // A refresh does not evict valid connections
    pool.refresh();
    Assert.assertEquals(2, pool.size());
    Assert.assertFalse(conn1.closed);
    Assert.assertFalse(conn2.closed);
    // A refresh evicts stale connections
    conn1.valid = false;
    pool.refresh();
    assertWaitUntil(() -> pool.size() == 1);
    Assert.assertTrue(conn1.closed);
    Assert.assertFalse(conn2.closed);
    Assert.assertEquals(1, pool.capacity());
    // Borrowing evicts stale connections before serving: the acquisition gets a fresh connection
    conn2.valid = false;
    Connection conn3 = new Connection();
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      Assert.assertSame(conn3, lease.get());
      Assert.assertTrue(conn2.closed);
      testComplete();
    }));
    mgr.assertRequest().connect(conn3, 0);
    await();
  }

  @Test
  public void testConnectRetryDelay() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    DefaultConnectionPool<Connection> pool = DefaultConnectionPool.builder(mgr)
      .withMaxSizes(new int[] { 1 })
      .withMaxWaiters(10)
      .withConnectRetryDelay(Duration.ofMillis(20))
      .build();
    pool.acquire(context, 0, (res, err) -> {
      // Fails with the connect failure
    });
    AtomicBoolean served = new AtomicBoolean();
    Connection expected = new Connection();
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      Assert.assertSame(expected, lease.get());
      served.set(true);
    }));
    mgr.assertRequest().fail(new Exception("not yet"));
    // The connect attempts are suspended
    pool.refresh();
    mgr.assertNoRequest();
    // Until the delay has passed
    assertWaitUntil(() -> {
      pool.refresh();
      return mgr.hasRequest();
    });
    mgr.assertRequest().connect(expected, 0);
    assertWaitUntil(served::get);
  }

  @Test
  public void testConnectSuccessClearsRetryDelay() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    DefaultConnectionPool<Connection> pool = DefaultConnectionPool.builder(mgr)
      .withMaxSizes(new int[] { 2 })
      .withMaxWaiters(10)
      .withConnectRetryDelay(Duration.ofSeconds(10))
      .build();
    pool.acquire(context, 0, (res, err) -> {
      // Fails with the connect failure
    });
    Connection conn1 = new Connection();
    AtomicBoolean served = new AtomicBoolean();
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      Assert.assertSame(conn1, lease.get());
      served.set(true);
    }));
    ConnectionRequest request1 = mgr.assertRequest();
    ConnectionRequest request2 = mgr.assertRequest();
    // The failure triggers the delay
    request1.fail(new Exception("failure"));
    mgr.assertNoRequest();
    // A third acquisition falls in the wait queue, the delay suspends the connect
    Connection conn2 = new Connection();
    AtomicBoolean served2 = new AtomicBoolean();
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      Assert.assertSame(conn2, lease.get());
      served2.set(true);
    }));
    mgr.assertNoRequest();
    // The success clears the delay: it serves the covered waiter and connects for the waiting one
    request2.connect(conn1, 0);
    assertWaitUntil(served::get);
    mgr.assertRequest().connect(conn2, 0);
    assertWaitUntil(served2::get);
  }

  @Test
  public void testAcquireTimeout() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = pool(mgr, new int[] { 1 });
    Connection conn1 = new Connection();
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      // The connection is held, the next acquisition waits
    }));
    mgr.assertRequest().connect(conn1, 0);
    AtomicBoolean timedOut = new AtomicBoolean();
    pool.acquire(context, new PoolWaiter.Listener<>() {
      @Override
      public void onTimeout(PoolWaiter<Connection> waiter) {
        timedOut.set(true);
      }
    }, 0, Duration.ofMillis(20), (res, err) -> Assert.fail());
    assertWaitUntil(() -> pool.waiters() == 1);
    assertWaitUntil(() -> {
      pool.refresh();
      return timedOut.get();
    });
    Assert.assertEquals(0, pool.waiters());
  }


  @Test
  public void testConnectFailureRetainsWaiterTimeout() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = pool(mgr, new int[] { 1 });
    // A first acquisition connects
    AtomicBoolean failed = new AtomicBoolean();
    pool.acquire(context, 0, (res, err) -> {
      if (err != null) {
        failed.set(true);
      }
    });
    ConnectionRequest request = mgr.assertRequest();
    // A second acquisition waits with a deadline, a third one waits
    AtomicBoolean timedOut = new AtomicBoolean();
    pool.acquire(context, new PoolWaiter.Listener<>() {
      @Override
      public void onTimeout(PoolWaiter<Connection> waiter) {
        timedOut.set(true);
      }
    }, 0, Duration.ofMillis(10), (res, err) -> {
    });
    pool.acquire(context, 0, (res, err) -> {
    });
    Thread.sleep(20);
    // The connect failure opens the round: the timed out waiter eviction and the connect failure
    // policy are both signalled
    request.fail(new Exception("failure"));
    assertWaitUntil(failed::get);
    Assert.assertTrue(timedOut.get());
  }

  @Test
  public void testMinSizeRequiresConnectRetryDelay() {
    ConnectionManager mgr = new ConnectionManager();
    try {
      DefaultConnectionPool.builder(mgr)
        .withMaxSizes(new int[] { 5 })
        .withMinSizes(new int[] { 1 })
        .withContextSupplier(() -> vertx.createEventLoopContext())
        .build();
      Assert.fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testMinSize() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    DefaultConnectionPool<Connection> pool = DefaultConnectionPool.builder(mgr)
      .withMaxSizes(new int[] { 5 })
      .withMinSizes(new int[] { 3 })
      .withContextSupplier(() -> context)
      .withConnectRetryDelay(Duration.ofSeconds(10))
      .build();
    // The floor is satisfied one connection at a time from the first execution round, extra
    // refreshes do not trigger extra connect requests while one is in flight
    pool.refresh();
    pool.refresh();
    pool.refresh();
    ConnectionRequest request = mgr.assertRequest();
    mgr.assertNoRequest();
    request.connect(new Connection(), 0);
    mgr.assertRequest().connect(new Connection(), 0);
    mgr.assertRequest().connect(new Connection(), 0);
    mgr.assertNoRequest();
    assertWaitUntil(() -> pool.size() == 3);
    // An acquisition is served from the pooled connections
    AtomicBoolean served = new AtomicBoolean();
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      served.set(true);
    }));
    assertWaitUntil(served::get);
    mgr.assertNoRequest();
  }

  @Test
  public void testMinSizeCoversWaiter() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    DefaultConnectionPool<Connection> pool = DefaultConnectionPool.builder(mgr)
      .withMaxSizes(new int[] { 5 })
      .withMinSizes(new int[] { 1 })
      .withContextSupplier(() -> context)
      .withConnectRetryDelay(Duration.ofSeconds(10))
      .build();
    pool.refresh();
    ConnectionRequest request = mgr.assertRequest();
    // A waiter arriving while the floor connect is in flight is covered by it
    Connection expected = new Connection();
    AtomicBoolean served = new AtomicBoolean();
    pool.acquire(context, 0, TestUtils.onSuccess2(lease -> {
      Assert.assertSame(expected, lease.get());
      served.set(true);
    }));
    mgr.assertNoRequest();
    request.connect(expected, 0);
    assertWaitUntil(served::get);
    Assert.assertEquals(1, pool.size());
  }

  @Test
  public void testMinSizeConnectRetryDelay() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    DefaultConnectionPool<Connection> pool = DefaultConnectionPool.builder(mgr)
      .withMaxSizes(new int[] { 5 })
      .withMinSizes(new int[] { 1 })
      .withContextSupplier(() -> context)
      .withConnectRetryDelay(Duration.ofMillis(20))
      .build();
    pool.refresh();
    mgr.assertRequest().fail(new Exception("not yet"));
    // The failure suspends the floor
    pool.refresh();
    mgr.assertNoRequest();
    // Until the delay has passed
    assertWaitUntil(() -> {
      pool.refresh();
      return mgr.hasRequest();
    });
    // The retry fails: the floor is suspended again
    mgr.assertRequest().fail(new Exception("not yet"));
    pool.refresh();
    mgr.assertNoRequest();
    // The next retry succeeds
    assertWaitUntil(() -> {
      pool.refresh();
      return mgr.hasRequest();
    });
    Connection conn = new Connection();
    mgr.assertRequest().connect(conn, 0);
    assertWaitUntil(() -> pool.size() == 1);
  }

  @Test
  public void testMinCapacity() {
    // HTTP/1 floor: an HTTP/1 back-end converges to the minimum connections one at a time,
    // a single HTTP/2 connection satisfies the floor without overshooting
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 3, 0 }, 0, 0, 3, 3);
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 3, 0 }, 0, 1, 1, 5);
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 3, 0 }, 1, 0, 3, 3);
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 3, 0 }, 1, 1, 1, 5);
    // HTTP/2 floor: an HTTP/1 back-end converges from below to the equivalent capacity, an
    // HTTP/2 back-end is satisfied by one connection
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 0, 1 }, 0, 0, 5, 5);
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 0, 1 }, 0, 1, 1, 5);
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 0, 1 }, 1, 0, 5, 5);
    assertMinCapacity(new int[] { 5, 1 }, new int[] { 0, 1 }, 1, 1, 1, 5);
  }

  private void assertMinCapacity(int[] maxSizes, int[] minSizes, int defaultKind, int connectKind,
                                 int expectedSize, int expectedCapacity) {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    DefaultConnectionPool<Connection> pool = DefaultConnectionPool.builder(mgr)
      .withMaxSizes(maxSizes)
      .withMinSizes(minSizes)
      .withDefaultKind(defaultKind)
      .withContextSupplier(() -> context)
      .withConnectRetryDelay(Duration.ofSeconds(10))
      .build();
    while (true) {
      pool.refresh();
      if (!mgr.hasRequest()) {
        break;
      }
      mgr.assertRequest().connect(new Connection(), connectKind);
    }
    Assert.assertEquals(expectedSize, pool.size());
    Assert.assertEquals(expectedCapacity, pool.capacity());
  }

  @Override
  protected <C> ConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes) {
    return DefaultConnectionPool.pool(connector, maxSizes);
  }

  @Override
  protected <C> ConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes, int maxWaiters) {
    return DefaultConnectionPool.pool(connector, maxSizes, maxWaiters);
  }
}
