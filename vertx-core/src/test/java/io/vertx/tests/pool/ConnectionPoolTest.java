/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.pool;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.pool.*;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConnectionPoolTest extends VertxTestBase {

  VertxInternal vertx;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.vertx = (VertxInternal) super.vertx;
  }

  @Test
  public void testConnect() {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 10 }, 10);
    Connection expected = new Connection();
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
      assertSame(expected, lease.get());
      assertEquals(0, pool.requests());
      testComplete();
    }));
    assertEquals(1, pool.requests());
    ConnectionRequest request = mgr.assertRequest();
    assertSame(context.nettyEventLoop(), request.context.nettyEventLoop());
    request.connect(expected, 0);
    await();
  }

  @Test
  public void testAcquireRecycledConnection() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 10 });
    Connection expected = new Connection();
    CountDownLatch latch = new CountDownLatch(1);
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        lease.recycle();
        latch.countDown();
      }));
    ConnectionRequest request = mgr.assertRequest();
    assertSame(context.nettyEventLoop(), request.context.nettyEventLoop());
    request.connect(expected, 0);
    awaitLatch(latch);
    pool.acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        assertSame(expected, lease.get());
        testComplete();
      }));
    await();
  }

  @Test
  public void testRecycleRemovedConnection() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 10 }, 10);
    Connection expected1 = new Connection();
    Future<Lease<Connection>> fut = pool.acquire(context, 0);
    ConnectionRequest request1 = mgr.assertRequest();
    request1.connect(expected1, 0);
    CountDownLatch latch = new CountDownLatch(1);
    fut.onComplete(onSuccess(lease -> {
      request1.listener.onRemove();
      lease.recycle();
      latch.countDown();
    }));
    awaitLatch(latch);
    Connection expected2 = new Connection();
    pool.acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        assertSame(expected2, lease.get());
        testComplete();
      }));
    ConnectionRequest request2 = mgr.assertRequest();
    request2.connect(expected2, 0);
    await();
  }

  @Test
  public void testConcurrency() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 10 }, 10);
    Connection expected = new Connection();
    CountDownLatch latch = new CountDownLatch(1);
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(conn -> {
        latch.countDown();
      }));
    ConnectionRequest request = mgr.assertRequest();
    request.concurrency(2).connect(expected, 0);
    awaitLatch(latch);
    pool.acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        assertSame(lease.get(), expected);
        testComplete();
      }));
    await();
  }

  @Test
  public void testIncreaseConcurrency() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    ContextInternal ctx = vertx.createEventLoopContext();
    Connection conn1 = new Connection();
    CountDownLatch l1 = new CountDownLatch(1);
    pool.acquire(ctx, 0).onComplete(onSuccess(lease -> l1.countDown()));
    CountDownLatch l2 = new CountDownLatch(1);
    pool
      .acquire(ctx, 0)
      .onComplete(onSuccess(lease -> {
        l2.countDown();
      }));
    CountDownLatch l3 = new CountDownLatch(1);
    pool.acquire(ctx, 0).onComplete(onSuccess(lease -> l3.countDown()));
    ConnectionRequest request = mgr.assertRequest();
    request.connect(conn1, 0);
    awaitLatch(l1);
    assertEquals(1, l2.getCount());
    request.listener.onConcurrencyChange(2);
    awaitLatch(l2);
    request.listener.onConcurrencyChange(3);
    awaitLatch(l3);
  }

  @Test
  public void testSatisfyPendingWaitersWithExtraConcurrency() throws Exception {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 2);
    Connection expected = new Connection();
    AtomicInteger seq = new AtomicInteger();
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        assertSame(lease.get(), expected);
        assertEquals(0, seq.getAndIncrement());
      }));
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        assertSame(lease.get(), expected);
        assertEquals(1, seq.getAndIncrement());
        testComplete();
      }));
    ConnectionRequest request = mgr.assertRequest();
    request.concurrency(2).connect(expected, 0);
    await();
  }

  @Test
  public void testEmptyConcurrency() {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 2);
    Connection expected = new Connection();
    AtomicInteger seq = new AtomicInteger();
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        assertSame(lease.get(), expected);
        assertEquals(1, seq.getAndIncrement());
      }));
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        assertSame(lease.get(), expected);
        assertEquals(2, seq.getAndIncrement());
        testComplete();
      }));
    ConnectionRequest request = mgr.assertRequest();
    request.concurrency(0).connect(expected, 0);
    assertEquals(0, seq.getAndIncrement());
    request.concurrency(2);
    await();
  }

  @Test
  public void testDecreaseConcurrency() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    ContextInternal ctx = vertx.createEventLoopContext();
    Connection conn1 = new Connection();
    CountDownLatch l1 = new CountDownLatch(2);
    CountDownLatch l2 = new CountDownLatch(1);
    Lease<Connection>[] leases = new Lease[3];
    pool
      .acquire(ctx, 0)
      .onComplete(onSuccess(lease -> {
        leases[0] = lease;
        l1.countDown();
      }));
    pool
      .acquire(ctx, 0)
      .onComplete(onSuccess(lease -> {
        leases[1] = lease;
        l1.countDown();
      }));
    pool
      .acquire(ctx, 0)
      .onComplete(onSuccess(lease -> {
        leases[2] = lease;
        l2.countDown();
      }));
    ConnectionRequest request = mgr.assertRequest();
    request.concurrency(2).connect(conn1, 0);
    awaitLatch(l1);
    assertEquals(1, l2.getCount());
    request.listener.onConcurrencyChange(1);
    ctx.runOnContext(v -> {
      leases[0].recycle();
      assertEquals(1, l2.getCount());
      leases[1].recycle();
      assertEquals(0, l2.getCount());
      testComplete();
    });
    await();
  }

  @Test
  public void testWaiter() throws Exception {
    ContextInternal ctx1 = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    Connection expected = new Connection();
    CompletableFuture<Lease<Connection>> latch = new CompletableFuture<>();
    pool.acquire(ctx1, 0).onComplete(onSuccess(latch::complete));
    ConnectionRequest request = mgr.assertRequest();
    request.connect(expected, 0);
    Lease<Connection> lease1 = latch.get(10, TimeUnit.SECONDS);
    AtomicBoolean recycled = new AtomicBoolean();
    ContextInternal ctx2 = vertx.createEventLoopContext();
    pool
      .acquire(ctx2, 0)
      .onComplete(onSuccess(lease2 -> {
        assertSame(ctx1.nettyEventLoop(), ((ContextInternal) Vertx.currentContext()).nettyEventLoop());
        assertTrue(recycled.get());
        testComplete();
      }));
    assertEquals(1, pool.waiters());
    recycled.set(true);
    lease1.recycle();
    await();
  }

  @Test
  public void testRemoveSingleConnection() throws Exception {
    ContextInternal ctx1 = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 1);
    Connection conn = new Connection();
    CompletableFuture<Lease<Connection>> latch = new CompletableFuture<>();
    pool.acquire(ctx1, 0).onComplete(onSuccess(latch::complete));
    ConnectionRequest request = mgr.assertRequest();
    request.connect(conn, 0);
    latch.get(10, TimeUnit.SECONDS);
    request.listener.onRemove();
    assertEquals(0, pool.size());
    assertEquals(0, pool.capacity());
  }

  @Test
  public void testRemoveFirstConnection() throws Exception {
    ContextInternal ctx = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 2 }, 2);
    Connection conn1 = new Connection();
    CompletableFuture<Lease<Connection>> latch1 = new CompletableFuture<>();
    pool.acquire(ctx, 0).onComplete(onSuccess(latch1::complete));
    Connection conn2 = new Connection();
    CompletableFuture<Lease<Connection>> latch2 = new CompletableFuture<>();
    pool.acquire(ctx, 0).onComplete(onSuccess(latch2::complete));
    ConnectionRequest request1 = mgr.assertRequest();
    request1.connect(conn1, 0);
    ConnectionRequest request2 = mgr.assertRequest();
    request2.connect(conn2, 0);
    latch1.get(10, TimeUnit.SECONDS);
    request1.listener.onRemove();
    assertEquals(1, pool.size());
    assertEquals(1, pool.capacity());
  }

  @Test
  public void testRemoveSingleConnectionWithWaiter() throws Exception {
    ContextInternal ctx1 = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    Connection connection1 = new Connection();
    CompletableFuture<Lease<Connection>> latch = new CompletableFuture<>();
    pool
      .acquire(ctx1, 0)
      .onComplete(onSuccess(latch::complete));
    ConnectionRequest request1 = mgr.assertRequest();
    request1.connect(connection1, 0);
    Lease<Connection> lease1 = latch.get(10, TimeUnit.SECONDS);
    assertSame(connection1, lease1.get());
    AtomicBoolean evicted = new AtomicBoolean();
    Connection conn2 = new Connection();
    ContextInternal ctx2 = vertx.createEventLoopContext();
    pool
      .acquire(ctx2, 0)
      .onComplete(onSuccess(lease2 -> {
        assertSame(ctx2.nettyEventLoop(), ((ContextInternal) Vertx.currentContext()).nettyEventLoop());
        assertTrue(evicted.get());
        assertSame(conn2, lease2.get());
        testComplete();
      }));
    assertEquals(1, pool.waiters());
    evicted.set(true);
    request1.listener.onRemove();
    ConnectionRequest request2 = mgr.assertRequest();
    request2.connect(conn2, 0);
    await();
  }

  @Test
  public void testConnectFailureWithPendingWaiter() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1, 2 }, 2);
    Throwable failure = new Throwable();
    Connection expected = new Connection();
    CountDownLatch latch = new CountDownLatch(1);
    ContextInternal ctx1 = vertx.createEventLoopContext();
    pool
      .acquire(ctx1, 0)
      .onComplete(onFailure(cause -> {
        assertSame(failure, cause);
        assertEquals(1, pool.requests());
        latch.countDown();
      }));
    ContextInternal ctx2 = vertx.createEventLoopContext();
    pool.acquire(ctx2, 1)
      .onComplete(onSuccess(lease -> {
        assertSame(expected, lease.get());
        testComplete();
      }));
    ConnectionRequest request1 = mgr.assertRequest();
    assertEquals(2, pool.capacity());
    request1.fail(failure);
    awaitLatch(latch);
    assertEquals(1, pool.capacity());
    ConnectionRequest request2 = mgr.assertRequest();
    request2.connect(expected, 0);
    await();
  }

  @Test
  public void testExpireFirst() throws Exception {
    assertEquals(Arrays.asList(0), testExpire(1, 10, 0));
    assertEquals(Arrays.asList(0), testExpire(2, 10, 0));
    assertEquals(Arrays.asList(0), testExpire(3, 10, 0));
  }

  @Test
  public void testExpireLast() throws Exception {
    assertEquals(Arrays.asList(0), testExpire(1, 10, 0));
    assertEquals(Arrays.asList(1), testExpire(2, 10, 1));
    assertEquals(Arrays.asList(2), testExpire(3, 10, 2));
  }

  @Test
  public void testExpireMiddle() throws Exception {
    assertEquals(Arrays.asList(1), testExpire(3, 10, 1));
  }

  @Test
  public void testExpireSome() throws Exception {
    assertEquals(Arrays.asList(2, 1), testExpire(3, 10, 1, 2));
    assertEquals(Arrays.asList(2, 1, 0), testExpire(3, 10, 0, 1, 2));
    assertEquals(Arrays.asList(1, 0), testExpire(3, 10, 0, 1));
  }

  private List<Integer> testExpire(int num, int max, int... recycled) throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { max }, max);
    CountDownLatch latch = new CountDownLatch(num);
    List<Lease<Connection>> leases = new ArrayList<>();
    ContextInternal ctx = vertx.createEventLoopContext();
    for (int i = 0;i < num;i++) {
      Connection expected = new Connection();
      pool
        .acquire(ctx, 0)
        .onComplete(onSuccess(lease -> {
          assertSame(expected, lease.get());
          leases.add(lease);
          latch.countDown();
        }));
      mgr.assertRequest().connect(expected, 0);
    }
    awaitLatch(latch);
    for (int i = 0;i < recycled.length;i++) {
      leases.get(recycled[i]).recycle();
    }
    CompletableFuture<List<Integer>> cf = new CompletableFuture<>();
    pool
      .evict(c -> true)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          // assertEquals(num - recycled.length, pool.capacity());
          List<Integer> res = new ArrayList<>();
          List<Connection> all = leases.stream().map(Lease::get).collect(Collectors.toList());
          ar.result().forEach(c -> res.add(all.indexOf(c)));
          cf.complete(res);
        } else {
          cf.completeExceptionally(ar.cause());
        }
      });
    return cf.get();
  }

  @Test
  public void testRemoveEvicted() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 1);
    // List<Lease<Connection>> leases = new ArrayList<>();
    ContextInternal ctx = vertx.createEventLoopContext();
    CountDownLatch latch1 = new CountDownLatch(1);
    pool
      .acquire(ctx, 0)
      .onComplete(onSuccess(lease -> {
        lease.recycle();
        latch1.countDown();
      }));
    ConnectionRequest request = mgr.assertRequest();
    Connection conn = new Connection();
    request.connect(conn, 0);
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    pool.evict(c -> c == conn).onComplete(onSuccess(l -> latch2.countDown()));
    awaitLatch(latch2);
    request.listener.onRemove();
    assertEquals(0, pool.size());
  }

  @Test
  public void testSynchronousEviction() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 1);
    ContextInternal ctx = vertx.createEventLoopContext();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    CountDownLatch latch3 = new CountDownLatch(1);
    pool
      .acquire(ctx, 0)
      .onComplete(onSuccess(lease -> {
        lease.recycle();
        latch1.countDown();
      }));
    ConnectionRequest request = mgr.assertRequest();
    Connection conn1 = new Connection();
    request.connect(conn1, 0);
    awaitLatch(latch1);
    Connection conn2 = new Connection();
    pool.evict(candidate -> {
      assertSame(candidate, conn1);
      pool
        .acquire(ctx, 0)
        .onComplete(onSuccess(lease -> {
          Connection c2 = lease.get();
          assertSame(conn2, c2);
          latch3.countDown();
        }));
      return true;
    }).onComplete(onSuccess(list -> {
      latch2.countDown();
    }));
    awaitLatch(latch2);
    request = mgr.assertRequest();
    request.connect(conn2, 0);
    awaitLatch(latch3);
  }

  @Test
  public void testConnectionInProgressShouldNotBeEvicted() {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 5);
    ContextInternal ctx = vertx.createEventLoopContext();
    pool.acquire(ctx, 0);
    mgr.assertRequest();
    pool
      .evict(c -> {
        fail();
        return false;
      }).onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testRecycleRemoveConnection() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 1);
    Connection expected = new Connection();
    CompletableFuture<Lease<Connection>> latch = new CompletableFuture<>();
    ContextInternal ctx1 = vertx.createEventLoopContext();
    pool.acquire(ctx1, 0).onComplete(onSuccess(latch::complete));
    ConnectionRequest request = mgr.assertRequest();
    request.connect(expected, 0);
    Lease<Connection> lease = latch.get();
    request.listener.onRemove();
    assertEquals(0, pool.size());
    lease.recycle();
    assertEquals(0, pool.size());
  }

  @Test
  public void testRecycleMultiple() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 1);
    Connection expected = new Connection();
    CompletableFuture<Lease<Connection>> latch = new CompletableFuture<>();
    ContextInternal ctx1 = vertx.createEventLoopContext();
    pool.acquire(ctx1, 0).onComplete(onSuccess(latch::complete));
    ConnectionRequest request = mgr.assertRequest();
    request.connect(expected, 0);
    Lease<Connection> lease = latch.get();
    lease.recycle();
    try {
      lease.recycle();
      fail();
    } catch (IllegalStateException ignore) {
    }
  }

  @Test
  public void testMaxWaiters() {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 5);
    ContextInternal ctx = vertx.createEventLoopContext();
    for (int i = 0;i < (5);i++) {
      pool
        .acquire(ctx, 0)
        .onComplete(ar -> fail());
    }
    pool
      .acquire(ctx, 0)
      .onComplete(onFailure(err -> {
        assertTrue(err instanceof ConnectionPoolTooBusyException);
        testComplete();
      }));
    await();
  }

  @Test
  public void testHeterogeneousSizes() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 5, 2 });
    ContextInternal ctx = vertx.createEventLoopContext();
    CountDownLatch latch = new CountDownLatch(5);
    for (int i = 0;i < 5;i++) {
      pool.acquire(ctx, 0).onComplete(onSuccess(lease -> latch.countDown()));
      Connection conn = new Connection();
      mgr.assertRequest().connect(conn, 0);
    }
    awaitLatch(latch);
    assertEquals(10, pool.capacity());
    pool
      .acquire(ctx, 1)
      .onComplete(onSuccess(lease -> {

      }));
    assertEquals(1, pool.waiters());
  }

  @Test
  public void testClose() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 2 }, 2);
    ContextInternal ctx = vertx.createEventLoopContext();
    Connection conn1 = new Connection();
    pool
      .acquire(ctx, 0)
      .onComplete(onSuccess(lease -> {
      }));
    waitFor(3);
    pool.acquire(ctx, 0).onComplete(onFailure(err -> complete()));
    pool.acquire(ctx, 0).onComplete(onFailure(err -> complete()));
    mgr.assertRequest().connect(conn1, 0);
    mgr.assertRequest();
    pool
      .close()
      .onComplete(onSuccess(lst -> {
        assertEquals(2, lst.size());
        assertEquals(0, pool.size());
        complete();
      }));
    await();
  }

  @Test
  public void testCloseTwice() throws Exception {
    AtomicBoolean isReentrant = new AtomicBoolean();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 2 }, 2);
    CountDownLatch latch = new CountDownLatch(1);
    pool
      .close()
      .onComplete(onSuccess(lst -> {
        AtomicBoolean inCallback = new AtomicBoolean();
        pool
          .close()
          .onComplete(onFailure(err -> {
            isReentrant.set(inCallback.get());
            latch.countDown();
          }));
      }));
    awaitLatch(latch);
    assertFalse(isReentrant.get());
  }

  @Test
  public void testUseAfterClose() throws Exception {
    waitFor(3);
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    ContextInternal ctx = vertx.createEventLoopContext();
    CompletableFuture<PoolWaiter<Connection>> waiterFut = new CompletableFuture<>();
    pool.acquire(ctx, new PoolWaiter.Listener<>() {
      @Override
      public void onConnect(PoolWaiter<Connection> waiter) {
        waiterFut.complete(waiter);
      }
    }, 0);
    PoolWaiter<Connection> waiter = waiterFut.get(20, TimeUnit.SECONDS);
    ConnectionRequest request = mgr.assertRequest();
    CountDownLatch latch = new CountDownLatch(1);
    pool
      .close()
      .onComplete(onSuccess(lst -> {
        latch.countDown();
      }));
    awaitLatch(latch);
    pool.evict(c -> true).onComplete(onFailure(err -> complete()));
    pool.acquire(ctx, 0).onComplete(onFailure(err -> complete()));
    pool.cancel(waiter).onComplete(onFailure(err -> complete()));
    request.connect(new Connection(), 0);
    await();
  }

  @Test
  public void testAcquireClosedConnection() throws Exception {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    ContextInternal context = vertx.createEventLoopContext();
    pool.acquire(context, 0).onComplete(onSuccess(Lease::recycle));
    Connection expected = new Connection();
    ConnectionRequest request = mgr.assertRequest();
    request.connect(expected, 0);
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    context.runOnContext(v -> {
      pool.evict(conn -> {
        // Make sure that the event-loop thread is busy and pool lock are borrowed
        latch1.countDown();
        try {
          // Wait until the acquisition and removal tasks are enqueued
          latch2.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        // When we return, the tasks will be executed by this thread
        // but the acquisition callback is a pool post action executed after the removal task is executed
        return false;
      });
    });
    awaitLatch(latch1);
    AtomicBoolean closed = new AtomicBoolean();
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        // Get not null closed connection
        assertNotNull(lease.get());
        assertTrue(closed.get());
        testComplete();
      }));
    request.listener.onRemove();
    closed.set(true);
    latch2.countDown();
    await();
  }

  @Test
  public void testConnectSuccessAfterClose() {
    testConnectResultAfterClose(true);
  }

  @Test
  public void testConnectFailureAfterClose() {
    testConnectResultAfterClose(false);
  }

  private void testConnectResultAfterClose(boolean success) {
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    ContextInternal ctx = vertx.createEventLoopContext();
    AtomicInteger acquired = new AtomicInteger();
    pool
      .acquire(ctx, 0)
      .onComplete(ar -> {
        assertEquals(0, acquired.getAndIncrement());
      });
    assertEquals(1, pool.size());
    ConnectionRequest request = mgr.assertRequest();
    Future<List<Future<Connection>>> closeResult = pool.close();
    Throwable cause = new Throwable();
    Connection expected = new Connection();
    if (success) {
      request.connect(expected, 0);
    } else {
      request.fail(cause);
    }
    assertTrue(closeResult.isComplete());
    List<Future<Connection>> connections = closeResult.result();
    assertEquals(1, connections.size());
    assertEquals(success, connections.get(0).succeeded());
    assertEquals(0, pool.size());
    if (success) {
      assertEquals(expected, connections.get(0).result());
    } else {
      assertEquals(cause, connections.get(0).cause());
    }
    waitUntil(() -> acquired.get() == 1);
  }

  @Test
  public void testCancelQueuedWaiters() throws Exception {
    waitFor(1);
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 });
    CompletableFuture<PoolWaiter<Connection>> w = new CompletableFuture<>();
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
      }));
    pool.acquire(context, new PoolWaiter.Listener<>() {
        @Override
        public void onEnqueue(PoolWaiter<Connection> waiter) {
          w.complete(waiter);
        }
      }, 0)
      .onComplete(ar -> fail());
    PoolWaiter<Connection> waiter = w.get(10, TimeUnit.SECONDS);
    pool
      .cancel(waiter)
      .onComplete(onSuccess(removed1 -> {
        assertTrue(removed1);
        assertEquals(0, pool.waiters());
        pool
          .cancel(waiter)
          .onComplete(onSuccess(removed2 -> {
            assertFalse(removed2);
            assertEquals(0, pool.waiters());
            testComplete();
          }));
      }));
    await();
  }

  @Test
  public void testCancelWaiterBeforeConnectionSuccess() throws Exception {
    testCancelWaiterBeforeConnection(true, 0);
  }

  @Test
  public void testCancelWaiterBeforeConnectionSuccessWithExtraWaiters() throws Exception {
    testCancelWaiterBeforeConnection(true, 2);
  }

  @Test
  public void testCancelWaiterBeforeConnectionFailure() throws Exception {
    testCancelWaiterBeforeConnection(false, 0);
  }

  public void testCancelWaiterBeforeConnection(boolean success, int extra) throws Exception {
    if (!success && extra > 0) {
      throw new IllegalArgumentException();
    }
    waitFor(1);
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 1 + extra);
    CompletableFuture<PoolWaiter<Connection>> waiterLatch = new CompletableFuture<>();
    pool.acquire(context, new PoolWaiter.Listener<>() {
        @Override
        public void onConnect(PoolWaiter<Connection> waiter) {
          waiterLatch.complete(waiter);
        }
      }, 0)
      .onComplete(ar -> fail());
    waiterLatch.get(10, TimeUnit.SECONDS);
    CountDownLatch enqueuedLatch = new CountDownLatch(extra);
    CountDownLatch recycledLatch = new CountDownLatch(extra);
    for (int i = 0; i < extra; i++) {
      pool.acquire(context, new PoolWaiter.Listener<>() {
          @Override
          public void onEnqueue(PoolWaiter<Connection> waiter) {
            enqueuedLatch.countDown();
          }
        }, 0)
        .onComplete(onSuccess(conn -> {
          conn.recycle();
          recycledLatch.countDown();
        }));
    }
    awaitLatch(enqueuedLatch);
    ConnectionRequest request = mgr.assertRequest();
    CountDownLatch latch = new CountDownLatch(1);
    pool
      .cancel(waiterLatch.get(10, TimeUnit.SECONDS))
      .onComplete(onSuccess(removed -> {
        assertTrue(removed);
        latch.countDown();
      }));
    awaitLatch(latch);
    if (success) {
      request.connect(new Connection(), 0);
    } else {
      request.fail(new Throwable());
    }
    awaitLatch(recycledLatch);
    // Check we can acquire the same connection again
    CountDownLatch doneLatch = new CountDownLatch(extra);
    for (int i = 0;i < extra;i++) {
      pool
        .acquire(context, 0)
        .onComplete(onSuccess(conn -> {
          doneLatch.countDown();
          conn.recycle();
        }));
    }
    awaitLatch(doneLatch);
  }

  @Test
  public void testCancelWaiterAfterConnectionSuccess() throws Exception {
    testCancelWaiterAfterConnectionSuccess(true);
  }

  @Test
  public void testCancelWaiterAfterConnectionFailure() throws Exception {
    testCancelWaiterAfterConnectionSuccess(false);
  }

  public void testCancelWaiterAfterConnectionSuccess(boolean success) throws Exception {
    waitFor(1);
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 1 }, 1);
    CompletableFuture<PoolWaiter<Connection>> w = new CompletableFuture<>();
    CountDownLatch latch = new CountDownLatch(1);
    pool.acquire(context, new PoolWaiter.Listener<>() {
        @Override
        public void onConnect(PoolWaiter<Connection> waiter) {
          w.complete(waiter);
        }
      }, 0)
      .onComplete(ar -> {
        latch.countDown();
      });
    w.get(10, TimeUnit.SECONDS);
    ConnectionRequest request = mgr.assertRequest();
    if (success) {
      request.connect(new Connection(), 0);
    } else {
      request.fail(new Throwable());
    }
    awaitLatch(latch);
    pool
      .cancel(w.get(10, TimeUnit.SECONDS))
      .onComplete(onSuccess(removed -> {
        assertFalse(removed);
        testComplete();
      }));
    await();
  }

  @Test
  public void testConnectionSelector() throws Exception {
    waitFor(1);
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 2 });
    CountDownLatch latch1 = new CountDownLatch(1);
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        lease.recycle();
        latch1.countDown();
      }));
    Connection conn1 = new Connection();
    mgr.assertRequest().connect(conn1, 0);
    awaitLatch(latch1);
    pool.connectionSelector((waiter, list) -> {
      assertEquals(1, list.size());
      PoolConnection<Connection> pooled = list.get(0);
      assertEquals(1, pooled.available());
      assertEquals(1, pooled.concurrency());
      assertSame(conn1, pooled.get());
      assertSame(context.nettyEventLoop(), pooled.context().nettyEventLoop());
      assertSame(context, waiter.context());
      return pooled;
    });
    pool
      .acquire(context, 0)
      .onComplete(onSuccess(lease -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testDefaultSelector() throws Exception {
    ContextImpl context1 = (ContextImpl) vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 10 }, 10);
    CountDownLatch latch1 = new CountDownLatch(1);
    pool
      .acquire(context1, 0)
      .onComplete(onSuccess(lease -> {
        lease.recycle();
        latch1.countDown();
      }));
    Connection expected = new Connection();
    assertEquals(1, pool.requests());
    ConnectionRequest request = mgr.assertRequest();
    request.connect(expected, 0);
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    pool
      .acquire(context1, 0)
      .onComplete(onSuccess(lease -> {
        assertEquals(expected, lease.get());
        lease.recycle();
        latch2.countDown();
      }));
    awaitLatch(latch2);
    CountDownLatch latch3 = new CountDownLatch(1);
    ContextInternal context2 = vertx
      .contextBuilder()
      .withEventLoop(context1.nettyEventLoop())
      .withWorkerPool(context1.workerPool())
      .withClassLoader(context1.classLoader())
      .build();
    pool
      .acquire(context2, 0)
      .onComplete(onSuccess(lease -> {
        assertEquals(expected, lease.get());
        lease.recycle();
        latch3.countDown();
      }));
    awaitLatch(latch3);
  }

  @Test
  public void testDefaultContextProviderUnwrap() {
    ContextInternal context = vertx.createEventLoopContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 10 }, 10);
    pool
      .acquire(context.duplicate(), 0)
      .onComplete(onSuccess(lease -> {
      }));
    assertEquals(1, pool.requests());
    ConnectionRequest request = mgr.assertRequest();
    assertSame(context.nettyEventLoop(), request.context.nettyEventLoop());
  }

  @Test
  public void testDefaultContextProviderReusesSameEventLoop() {
    ContextInternal context = vertx.createWorkerContext();
    ConnectionManager mgr = new ConnectionManager();
    ConnectionPool<Connection> pool = ConnectionPool.pool(mgr, new int[] { 10 }, 10);
    pool
      .acquire(context.duplicate(), 0)
      .onComplete(onSuccess(lease -> {
      }));
    assertEquals(1, pool.requests());
    ConnectionRequest request = mgr.assertRequest();
    assertSame(context.nettyEventLoop(), request.context.nettyEventLoop());
  }

  @Test
  public void testPostTasksTrampoline() throws Exception {
    int numAcquires = 5;
    AtomicReference<ConnectionPool<Connection>> ref = new AtomicReference<>();
    ContextInternal ctx = vertx.createEventLoopContext();
    List<Integer> res = Collections.synchronizedList(new LinkedList<>());
    AtomicInteger seq = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(1 + numAcquires);
    int[] count = new int[1];
    ConnectionPool<Connection> pool = ConnectionPool.pool(new PoolConnector<Connection>() {
      int reentrancy = 0;
      @Override
      public Future<ConnectResult<Connection>> connect(ContextInternal context, Listener listener) {
        assertEquals(0, reentrancy++);
        try {
          int val = count[0]++;
          if (val == 0) {
            // Queue extra requests
            for (int i = 0;i < numAcquires;i++) {
              int num = seq.getAndIncrement();
              ref
                .get()
                .acquire(ctx, 0)
                .onComplete(onFailure(err -> {
                  res.add(num);
                  latch.countDown();
                }));
            }
            assertEquals(1, count[0]);
          }
          return Future.failedFuture("failure");
        } finally {
          reentrancy--;
        }
      }
      @Override
      public boolean isValid(Connection connection) {
        return true;
      }
    }, new int[]{1}, 1 + numAcquires);
    ref.set(pool);
    ctx.runOnContext(v -> {
      int num = seq.getAndIncrement();
      pool
        .acquire(ctx, 0)
        .onComplete(onFailure(err -> {
          res.add(num);
          latch.countDown();
        }));
    });
    awaitLatch(latch);
    assertEquals(1 + numAcquires, count[0]);
    List<Integer> expected = IntStream.concat(IntStream.range(1, numAcquires + 1), IntStream.of(0)).boxed().collect(Collectors.toList());
    assertEquals(expected, res);
  }

  @Test
  public void testConcurrentPostTasksTrampoline() throws Exception {
    AtomicReference<ConnectionPool<Connection>> ref1 = new AtomicReference<>();
    AtomicReference<ConnectionPool<Connection>> ref2 = new AtomicReference<>();
    ContextInternal ctx = vertx.createEventLoopContext();
    List<Integer> res = Collections.synchronizedList(new LinkedList<>());
    CountDownLatch latch = new CountDownLatch(4);
    ConnectionPool<Connection> pool1 = ConnectionPool.pool(new PoolConnector<>() {
      int count = 0;
      int reentrancy = 0;
      @Override
      public Future<ConnectResult<Connection>> connect(ContextInternal context, Listener listener) {
        assertEquals(0, reentrancy++);
        try {
          int val = count++;
          if (val == 0) {
            ref1
              .get()
              .acquire(ctx, 0)
              .onComplete(onFailure(err -> {
              res.add(1);
              latch.countDown();
            }));
            ref2
              .get()
              .acquire(ctx, 0)
              .onComplete(onFailure(err -> {
                res.add(2);
                latch.countDown();
              }));
          }
          return Future.failedFuture("failure");
        } finally {
          reentrancy--;
        }
      }
      @Override
      public boolean isValid(Connection connection) {
        return true;
      }
    }, new int[]{1}, 2);
    ConnectionPool<Connection> pool2 = ConnectionPool.pool(new PoolConnector<>() {
      int count = 0;
      int reentrancy = 0;
      @Override
      public Future<ConnectResult<Connection>> connect(ContextInternal context, Listener listener) {
        assertEquals(0, reentrancy++);
        try {
          int val = count++;
          if (val == 0) {
            ref2
              .get()
              .acquire(ctx, 0)
              .onComplete(onFailure(err -> {
                res.add(3);
                latch.countDown();
              }));
            ref1
              .get()
              .acquire(ctx, 0)
              .onComplete(onFailure(err -> {
                res.add(4);
                latch.countDown();
              }));
          }
          return Future.failedFuture("failure");
        } finally {
          reentrancy--;
        }
      }
      @Override
      public boolean isValid(Connection connection) {
        return true;
      }
    }, new int[]{1}, 2);
    ref1.set(pool1);
    ref2.set(pool2);
    pool1.acquire(ctx, 0).onComplete(onFailure(err -> res.add(0)));
    awaitLatch(latch);
//    assertEquals(Arrays.asList(0, 2, 1, 3, 4), res);
  }

  static class Connection {
    public Connection() {
    }
  }

  static class ConnectionRequest {
    final ContextInternal context;
    final PoolConnector.Listener listener;
    final Promise<ConnectResult<Connection>> completion;
    private int concurrency;
    private Connection connection;
    ConnectionRequest(ContextInternal context, PoolConnector.Listener listener, Promise<ConnectResult<Connection>> completion) {
      this.context = context;
      this.listener = listener;
      this.completion = completion;
      this.concurrency = 1;
    }
    void connect(Connection connection, int type) {
      if (this.connection != null) {
        throw new IllegalStateException();
      }
      this.connection = connection;
      completion.handle(Future.succeededFuture(new ConnectResult<>(connection, concurrency, type)));
    }
    ConnectionRequest concurrency(int value) {
      if (value < concurrency) {
        if (connection != null) {
          throw new IllegalStateException();
        }
        concurrency = value;
      } else {
        concurrency = value;
        listener.onConcurrencyChange(concurrency);
      }
      return this;
    }

    public void fail(Throwable cause) {
      completion.handle(Future.failedFuture(cause));
    }
  }

  class ConnectionManager implements PoolConnector<Connection> {

    private final Queue<ConnectionRequest> requests = new ArrayBlockingQueue<>(100);

    @Override
    public Future<ConnectResult<Connection>> connect(ContextInternal context, Listener listener) {
      Promise<ConnectResult<Connection>> promise = Promise.promise();
      requests.add(new ConnectionRequest(context, listener, promise));
      return promise.future();
    }

    @Override
    public boolean isValid(Connection connection) {
      return true;
    }

    ConnectionRequest assertRequest() {
      ConnectionRequest request = requests.poll();
      assertNotNull(request);
      return request;
    }
  }
}
