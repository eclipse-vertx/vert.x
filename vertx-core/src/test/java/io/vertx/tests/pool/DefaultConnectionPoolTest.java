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

  @Override
  protected <C> ConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes) {
    return DefaultConnectionPool.pool(connector, maxSizes);
  }

  @Override
  protected <C> ConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes, int maxWaiters) {
    return DefaultConnectionPool.pool(connector, maxSizes, maxWaiters);
  }
}
