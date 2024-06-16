package io.vertx.tests.json.jackson;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CountDownLatch;

import com.fasterxml.jackson.core.util.BufferRecycler;
import io.vertx.core.json.jackson.HybridJacksonPool;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HybridJacksonPoolTest {

  @Test
  public void testVirtualThreadPoolWithSingleThread() {
    HybridJacksonPool.StripedLockFreePool virtualPool = new HybridJacksonPool.StripedLockFreePool(4);
    BufferRecycler pooledResource = virtualPool.acquirePooled();
    assertEquals(0, virtualPool.size());
    virtualPool.releasePooled(pooledResource);
    assertEquals(1, virtualPool.size());

    // The same thread should get the same pooled resource
    assertSame(pooledResource, virtualPool.acquirePooled());
    assertEquals(0, virtualPool.size());
  }

  @Test
  public void testVirtualThreadPoolWithMultipleThreads() {
    // this test can run only on a jdk version that supports virtual threads
    Assume.assumeTrue(VirtualThreadRunner.hasVirtualThread());

    int stripesCount = 4;
    HybridJacksonPool.StripedLockFreePool virtualPool = new HybridJacksonPool.StripedLockFreePool(stripesCount);
    int nThreads = 100;
    BufferRecycler[] resources = new BufferRecycler[nThreads];
    CountDownLatch latch = new CountDownLatch(nThreads);

    for (int i = 0; i < nThreads; i++) {
      int threadIndex = i;
      VirtualThreadRunner.runOnVirtualThread(() -> {
        resources[threadIndex] = virtualPool.acquirePooled();
        latch.countDown();
      });
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    assertEquals(0, virtualPool.size());

    for (int i = 0; i < nThreads; i++) {
      virtualPool.releasePooled(resources[i]);
    }

    // check that all resources have been released back to the pool
    assertEquals(nThreads, virtualPool.size());

    int avgResourcesNrPerStripe = nThreads / stripesCount;
    int minResourcesNrPerStripe = avgResourcesNrPerStripe / 2;
    int maxResourcesNrPerStripe = avgResourcesNrPerStripe * 2;

    // check that all the stripes in the pool are reasonably balanced
    int[] poolStats = virtualPool.stackStats();
    for (int i = 0; i < stripesCount; i++) {
      assertTrue(poolStats[i] >= minResourcesNrPerStripe);
      assertTrue(poolStats[i] <= maxResourcesNrPerStripe);
    }
  }

  private static class VirtualThreadRunner {
    static final MethodHandle virtualMh = findVirtualMH();

    static MethodHandle findVirtualMH() {
      try {
        return MethodHandles.publicLookup().findStatic(Thread.class, "startVirtualThread",
                                                       MethodType.methodType(Thread.class, Runnable.class));
      } catch (Exception e) {
        return null;
      }
    }

    static boolean hasVirtualThread() {
      return virtualMh != null;
    }

    static void runOnVirtualThread(Runnable runnable) {
      try {
        VirtualThreadRunner.virtualMh.invoke(runnable);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }
}
