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
package io.vertx.tests.resource;

import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.resource.ResourceManager;
import io.vertx.core.internal.resource.ManagedResource;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ResourceManagerTest extends VertxTestBase {

  private static final Object TEST_KEY = new Object();

  public Future<Resource> getResource(ContextInternal ctx, ResourceManager<Object, TestResource> mgr, Function<Object, TestResource> provider, Object key) {
    return mgr.withResourceAsync(key, provider, (endpoint, created) -> endpoint.acquire(ctx, 0L));
  }

  static abstract class TestResource extends ManagedResource {
    public abstract Future<Resource> acquire(ContextInternal ctx, long timeout);
  }

  @Test
  public void testAcquireSuccess() {
    testAcquire(true);
  }

  @Test
  public void testAcquireFailure() {
    testAcquire(false);
  }

  private void testAcquire(boolean success) {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Resource result = new Resource();
    Throwable failure = new Throwable();
    Function<Object, TestResource> provider = key -> new TestResource() {
      @Override
      public Future<Resource> acquire(ContextInternal ctx1, long timeout) {
        incRefCount();
        if (success) {
          return ctx1.succeededFuture(result);
        } else {
          return ctx1.failedFuture(failure);
        }
      }
    };
    ResourceManager<Object, TestResource> mgr = new ResourceManager<>();
    getResource(ctx, mgr, provider, TEST_KEY).onComplete(ar -> {
      if (ar.succeeded()) {
        assertTrue(success);
        assertSame(result, ar.result());
      } else {
        assertFalse(success);
        assertSame(failure, ar.cause());
      }
      testComplete();
    });
    await();
  }

  @Test
  public void testDisposeAfterResourceClose() {
    testDispose(true);
  }

  @Test
  public void testDisposeAfterCallback() {
    testDispose(false);
  }

  private void testDispose(boolean closeConnectionAfterCallback) {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Resource expected = new Resource();
    AtomicReference<Runnable> postCheck = new AtomicReference<>();
    boolean[] disposed = new boolean[1];
    Function<Object, TestResource> provider = key -> new TestResource() {
      @Override
      public Future<Resource> acquire(ContextInternal ctx1, long timeout) {
        incRefCount();
        if (closeConnectionAfterCallback) {
          postCheck.set(() -> {
            assertFalse(disposed[0]);
            decRefCount();
            assertTrue(disposed[0]);
          });
          return ctx1.succeededFuture(expected);
        } else {
          decRefCount();
          assertFalse(disposed[0]);
          postCheck.set(() -> {
            assertTrue(disposed[0]);
          });
          return ctx1.succeededFuture(expected);
        }
      }

      @Override
      protected void cleanup() {
        disposed[0] = true;
      }
    };
    ResourceManager<Object, TestResource> mgr = new ResourceManager<>();
    getResource(ctx, mgr, provider, TEST_KEY).onComplete(onSuccess(conn -> {
      assertEquals(expected, conn);
      postCheck.get().run();
    }));
    waitUntil(() -> disposed[0]);
  }

  @Test
  public void testCloseManager() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Resource expected = new Resource();
    boolean[] disposed = new boolean[1];
    Function<Object, TestResource> provider = key -> new TestResource() {
      @Override
      public Future<Resource> acquire(ContextInternal ctx1, long timeout) {
        incRefCount();
        return ctx1.succeededFuture(expected);
      }

      @Override
      protected void cleanup() {
        disposed[0] = true;
      }

      @Override
      protected void handleClose() {
        decRefCount();
      }
    };
    ResourceManager<Object, TestResource> mgr = new ResourceManager<>();
    CountDownLatch latch = new CountDownLatch(1);
    getResource(ctx, mgr, provider, TEST_KEY).onComplete(onSuccess(conn -> {
      assertEquals(expected, conn);
      latch.countDown();
    }));
    awaitLatch(latch);
    assertFalse(disposed[0]);
    mgr.close();
    assertTrue(disposed[0]);
  }

  @Test
  public void testCloseManagerImmediately() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Resource expected = new Resource();
    boolean[] disposed = new boolean[1];
    AtomicReference<Runnable> adder = new AtomicReference<>();
    Function<Object, TestResource> provider = (key) -> new TestResource() {
      @Override
      public Future<Resource> acquire(ContextInternal ctx1, long timeout) {
        adder.set(() -> {
          incRefCount();
        });
        return ctx1.promise();
      }
    };
    ResourceManager<Object, TestResource> mgr = new ResourceManager<>();
    getResource(ctx, mgr, provider, TEST_KEY).onComplete(onSuccess(conn -> {
    }));
    waitUntil(() -> adder.get() != null);
    mgr.close();
    adder.get().run();
  }

  @Repeat(times = 20)
  @Test
  public void testConcurrentDispose() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ConcurrentLinkedQueue<AtomicBoolean> disposals = new ConcurrentLinkedQueue<>();
    Function<Object, TestResource> provider = key -> {
      AtomicBoolean disposed = new AtomicBoolean();
      disposals.add(disposed);
      return new TestResource() {
        @Override
        public Future<Resource> acquire(ContextInternal ctx1, long timeout) {
          if (disposed.get()) {
            // Check we don't have reentrant demands once disposed
            fail();
            return ctx1.promise();
          } else {
            Resource conn = new Resource();
            incRefCount();
            decRefCount();
            return ctx1.succeededFuture(conn);
          }
        }

        @Override
        protected void cleanup() {
          disposed.set(true);
        }
      };
    };
    ResourceManager<Object, TestResource> mgr = new ResourceManager<>();
    int num = 100000;
    int concurrency = 4;
    CountDownLatch[] latches = new CountDownLatch[concurrency];
    for (int i = 0;i < concurrency;i++) {
      CountDownLatch cc = new CountDownLatch(num);
      latches[i] = cc;
      new Thread(() -> {
        for (int j = 0;j < num;j++) {
          getResource(ctx, mgr, provider, TEST_KEY).onComplete(onSuccess(conn -> {
            cc.countDown();
          }));
        }
      }).start();
    }
    for (int i = 0;i < concurrency;i++) {
      awaitLatch(latches[i]);
    }
    disposals.forEach(disposed -> {
      waitUntil(disposed::get);
    });
  }

  static class Resource {
  }
}
