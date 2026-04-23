package io.vertx.tests.json.jackson;

import com.fasterxml.jackson.core.util.BufferRecycler;
import io.vertx.core.json.jackson.HybridJacksonPool;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

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
}
