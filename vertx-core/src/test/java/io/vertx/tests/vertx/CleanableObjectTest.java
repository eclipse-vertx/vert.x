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
import io.vertx.core.impl.CleanableObject;
import io.vertx.core.impl.CleanableResource;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

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
    TestResource resource = new TestResource();
    CleanableTest object = new CleanableTest(cleaner, resource);
    object = null;
    runGC(() -> resource.shutdownDuration != null);
    assertEquals(Duration.ofSeconds(30), resource.shutdownDuration);
  }

  @Test
  public void testExplicitCleanup() {
    Duration duration = Duration.ofSeconds(1);
    AtomicInteger count = new AtomicInteger();
    TestResource resource = new TestResource() {
      @Override
      public Future<Void> shutdown(Duration duration) {
        count.incrementAndGet();
        return super.shutdown(duration);
      }
    };
    CleanableTest object = new CleanableTest(cleaner, resource);
    object.release(duration);
    object.release(duration);
    assertEquals(duration, resource.shutdownDuration);
    assertEquals(1, count.get());
  }

  @Test
  public void testReclaimCleanableResource() {
    TestResource resource = new TestResource();
    CleanableTest object = new CleanableTest(cleaner, resource);
    object = null;
    runGC(() -> resource.shutdownDuration != null);
    assertEquals(Duration.ofSeconds(30), resource.shutdownDuration);
  }

  @Test
  public void testReleaseResourceCanBeCollected() {
    TestResource resource = new TestResource();
    CleanableTest object = new CleanableTest(cleaner, resource);
    WeakReference<TestResource> resourceRef = new WeakReference<>(resource);
    resource = null;
    runGC(() -> resourceRef.get() == null);
  }

  private static class TestResource implements CleanableResource<TestResource> {

    private volatile Duration shutdownDuration;
    private Promise<Void> shutdownCompletion = Promise.promise();

    @Override
    public TestResource get() {
      return this;
    }
    @Override
    public Future<Void> shutdown(Duration duration) {
      shutdownDuration = duration;
      return shutdownCompletion.future();
    }
  }

  private static class CleanableTest extends CleanableObject<TestResource> {
    public CleanableTest(Cleaner cleaner, TestResource resource) {
      super(cleaner, resource);
    }
    void release(Duration duration) {
      shutdown(duration);
    }
  }
}
