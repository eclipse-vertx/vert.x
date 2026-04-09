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

import io.vertx.core.impl.CleanableObject;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.vertx.tests.vertx.VertxTest.runGC;

public class CleanableObjectTest extends VertxTestBase {

  private Cleaner cleaner;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    cleaner = ((VertxInternal)vertx).cleaner();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    cleaner = null;
  }

  @Test
  public void testReclaimCleanableObject() {
    AtomicReference<Duration> shutdown = new AtomicReference<>();
    TestResource object = new TestResource(cleaner, shutdown::set);
    object = null;
    runGC(() -> shutdown.get() != null);
    assertEquals(Duration.ofSeconds(30), shutdown.get());
  }

  @Test
  public void testExplicitCleanup() {
    Duration duration = Duration.ofSeconds(1);
    AtomicReference<Duration> shutdown = new AtomicReference<>();
    AtomicInteger count = new AtomicInteger();
    TestResource object = new TestResource(cleaner, d -> {
      shutdown.set(d);
      count.incrementAndGet();
    });
    object.release(duration);
    object.release(duration);
    assertEquals(duration, shutdown.get());
    assertEquals(1, count.get());
  }

  private static class TestResource extends CleanableObject {
    public TestResource(Cleaner cleaner, Consumer<Duration> dispose) {
      super(cleaner, dispose);
    }
    void release(Duration duration) {
      clean(duration);
    }
  }
}
