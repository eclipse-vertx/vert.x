/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

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

  protected final int numNodes = 2;

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
      vi.getClusterManager().leave(onSuccess(v -> {
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
