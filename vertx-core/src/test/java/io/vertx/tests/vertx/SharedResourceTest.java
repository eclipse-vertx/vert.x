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
package io.vertx.tests.vertx;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.CloseableResource;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase2;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class SharedResourceTest extends VertxTestBase2 {

  @Test
  public void testSharedResource() {
    TestResource resource = testResource("val");
    VertxInternal vertx = (VertxInternal) this.vertx;
    CloseableResource<String> ref1 = vertx.createSharedResource("key", "name", resource.supplier);
    CloseableResource<String> ref2 = vertx.createSharedResource("key", "name", resource.supplier);
    assertEquals(1, resource.count);
    assertEquals("val", ref1.get());
    assertEquals("val", ref2.get());
    assertTrue(ref1.shutdown(Duration.ZERO).succeeded());
    assertNull(resource.shutdown());
    assertTrue(ref1.shutdown(Duration.ZERO).succeeded());
    assertNull(resource.shutdown());
    Future<Void> shutdown = ref2.shutdown(Duration.ZERO);
    assertFalse(shutdown.succeeded());
    assertNotNull(resource.shutdown());
    resource.succeedShutdown();
    assertTrue(shutdown.succeeded());
  }

  private static TestResource testResource(String value) {
    return new TestResource(value);
  }

  private static class TestResource implements CloseableResource<String> {

    private int count;
    private final Supplier<CloseableResource<String>> supplier;
    private final Promise<Void> completion;
    private final AtomicReference<Duration> shutdown;
    private final String value;

    public TestResource(String value) {
      this.completion = Promise.promise();
      this.shutdown = new AtomicReference<>();
      this.value = value;
      this.supplier = () -> {
        count++;
        return TestResource.this;
      };
    }

    public void succeedShutdown() {
      completion.succeed();
    }

    public Duration shutdown() {
      return shutdown.get();
    }

    @Override
    public String get() {
      return value;
    }

    @Override
    public Future<Void> shutdown(Duration duration) {
      if (shutdown.compareAndSet(null, duration)) {
        return completion.future();
      } else {
        throw new IllegalStateException();
      }
    }
  }
}
