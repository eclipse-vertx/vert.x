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

package io.vertx.core.shareddata;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredAsynchronousLockTest extends AsynchronousLockTest {

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  protected final int numNodes = 3;

  public void setUp() throws Exception {
    super.setUp();
    startNodes(numNodes);
  }

  AtomicInteger pos = new AtomicInteger();

  @Override
  protected Vertx getVertx() {
    int i = pos.incrementAndGet();
    i = mod(i, numNodes);
    return vertices[i];
  }

  private int mod(int idx, int size) {
    int i = idx % size;
    return i < 0 ? i + size : i;
  }

  @Test
  public void testGetLocalLock() {
    final Vertx node1 = getVertx();
    final Vertx node2 = getVertx();
    assertNotSame(node1, node2);
    AtomicInteger checkpoint = new AtomicInteger(1);

    CompositeFuture
      .all(node1.sharedData().getLocalLock("lock"), node2.sharedData().getLocalLock("lock"))
      .compose(compFuture -> {
      Lock lockNode1 = compFuture.result().resultAt(0);
      Lock lockNode2 = compFuture.result().resultAt(1);
      lockNode1.release();

      return node2.sharedData()
        .getLocalLockWithTimeout("lock", 250)
        .otherwise(t -> {
        assertEquals("Acquire lock should fail", "Timed out waiting to get lock", t.getMessage());
        checkpoint.decrementAndGet();
        return lockNode2;
      });
    }).onComplete(onSuccess(asyncLock -> {
      assertEquals(0, checkpoint.get());
      asyncLock.release();
      testComplete();
    }));
    await();
  }

  /**
   * Cannot run with the fake cluster manager.
   * Subclasses need to override the method and call <code>super.testLockReleasedForClosedNode()</code>.
   */
  @Test
  @Ignore
  public void testLockReleasedForClosedNode() throws Exception {
    testLockReleased(latch -> {
      vertices[0].close(onSuccess(v -> {
        latch.countDown();
      }));
    });
  }

  /**
   * Cannot run with the fake cluster manager.
   * Subclasses need to override the method and call <code>super.testLockReleasedForKilledNode()</code>.
   */
  @Test
  @Ignore
  public void testLockReleasedForKilledNode() throws Exception {
    testLockReleased(latch -> {
      VertxInternal vi = (VertxInternal) vertices[0];
      Promise<Void> promise = vi.getOrCreateContext().promise();
      vi.getClusterManager().leave(promise);
      promise.future().onComplete(onSuccess(v -> {
        latch.countDown();
      }));
    });
  }

  private void testLockReleased(Consumer<CountDownLatch> action) throws Exception {
    CountDownLatch lockAquiredLatch = new CountDownLatch(1);

    vertices[0].sharedData().getLockWithTimeout("pimpo", getLockTimeout(), onSuccess(lock -> {
      vertices[1].sharedData().getLockWithTimeout("pimpo", getLockTimeout(), onSuccess(lock2 -> {
        // Eventually acquired after node1 goes down
        testComplete();
      }));
      lockAquiredLatch.countDown();
    }));

    awaitLatch(lockAquiredLatch);

    CountDownLatch closeLatch = new CountDownLatch(1);
    action.accept(closeLatch);
    awaitLatch(closeLatch);

    await();
  }

  protected long getLockTimeout() {
    return 10000;
  }
}
