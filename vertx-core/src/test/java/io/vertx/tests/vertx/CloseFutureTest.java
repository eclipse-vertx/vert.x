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
package io.vertx.tests.vertx;

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.CloseFuture;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.tests.vertx.VertxTest.runGC;

public class CloseFutureTest extends AsyncTestBase {

  @Test
  public void testHookCompletion() {
    CloseFuture cf = new CloseFuture();
    AtomicReference<Promise<Void>> ref = new AtomicReference<>();
    cf.add(completion -> ref.set(completion));
    assertFalse(cf.close().succeeded());
    assertNotNull(ref.get());
    ref.get().complete();
    assertTrue(cf.close().succeeded());
  }

  @Test
  public void testRemoveDisposedCloseFutureHook() {
    CloseFuture cf = new CloseFuture();
    CloseFuture hook = new CloseFuture();
    cf.add(hook);
    assertTrue(hook.close().succeeded());
    cf.close();
    assertFalse(cf.remove(hook));
  }

  @Test
  public void testCloseFutureDuplicateClose() {
    AtomicReference<Promise<Void>> ref = new AtomicReference<>();
    CloseFuture fut = new CloseFuture();
    fut.add(ref::set);
    Promise<Void> p1 = Promise.promise();
    fut.close(p1);
    assertNotNull(ref.get());
    Promise<Void> p2 = Promise.promise();
    fut.close(p2);
    assertFalse(p1.future().isComplete());
    assertFalse(p2.future().isComplete());
    ref.get().complete();
    assertTrue(p1.future().isComplete());
    assertTrue(p2.future().isComplete());
  }

  @Test
  public void testCloseHook() {
    CloseFuture cf = new CloseFuture();
    CloseFuture hook = new CloseFuture();
    cf.add(hook);
    hook.close();
    assertFalse(cf.remove(hook));
    assertTrue(cf.close().succeeded());
  }

  private static final Cleaner cleaner = Cleaner.create();

  private interface UseCase {
    Future<Void> close();
  }

  private static final ThreadLocal<Object> closing = new ThreadLocal<>();

  private static class CleanableUseCase implements UseCase {

    private UseCaseResource resource;
    private Cleaner.Cleanable cleanable;

    public CleanableUseCase(UseCaseResource resource) {
      this.cleanable = cleaner.register(this, () -> {
        // SHOULD BLOCK ?
        boolean blocking = closing.get() == null;
        if (blocking) {
          Promise<Void> promise = Promise.promise();
          resource.close(promise);
        } else {
          resource.close(Promise.promise());
        }
      });
      this.resource = resource;
    }

    public Future<Void> close() {
      closing.set(true);
      try {
        cleanable.clean();
      } finally {
        closing.set(false);
      }
      return resource.closeFuture.future();
    }
  }

  private static class UseCaseResource implements Closeable, UseCase {

    private CloseFuture closeFuture = new CloseFuture();
    private AtomicBoolean closed = new AtomicBoolean();

    @Override
    public void close(Promise<Void> completion) {
      closed.set(true);
      completion.complete();
    }

    @Override
    public Future<Void> close() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testUseCaseGC() {
    UseCaseResource resource = new UseCaseResource();
    UseCase cleanable = new CleanableUseCase(resource);
    WeakReference<UseCase> ref = new WeakReference<>(cleanable);
    CloseFuture owner = new CloseFuture();
    owner.add(resource);
    cleanable = null;
    long now = System.currentTimeMillis();
    while (true) {
      assertTrue(System.currentTimeMillis() - now < 20_000);
      runGC();
      if (ref.get() == null && resource.closed.get()) {
        break;
      }
    }
  }

  @Test
  public void testUseCaseClose() {
    UseCaseResource resource = new UseCaseResource();
    UseCase cleanable = new CleanableUseCase(resource);
    CloseFuture owner = new CloseFuture();
    owner.add(resource);
    cleanable.close();
  }

  @Test
  public void testDetachFromCloseFutureOnCompletion() {
    CloseFuture closeFuture = new CloseFuture();
    CloseFuture nested = new CloseFuture();
    closeFuture.add(nested);
    nested.close();
    assertFalse(closeFuture.remove(nested));
  }
}
