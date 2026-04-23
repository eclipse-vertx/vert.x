package io.vertx.tests.jacksonv2;

import com.fasterxml.jackson.core.util.BufferRecycler;
import io.vertx.core.json.jackson.HybridJacksonPool;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class HybridJacksonPoolTest {

  @Test
  public void testVirtualThreadPoolWithMultipleThreads() {
    int stripesCount = 4;
    HybridJacksonPool.StripedLockFreePool virtualPool = new HybridJacksonPool.StripedLockFreePool(stripesCount);
    int nThreads = 100;
    BufferRecycler[] resources = new BufferRecycler[nThreads];
    CountDownLatch latch = new CountDownLatch(nThreads);

    for (int i = 0; i < nThreads; i++) {
      int threadIndex = i;
      Thread.startVirtualThread(() -> {
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
}
