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
package io.vertx.core.impl;

import io.vertx.core.Closeable;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.CloseableResource;

import java.time.Duration;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ResourceHook<R> implements CloseableResource<R>, Closeable {

  private final CloseFuture owner;
  private final CloseableResource<R> resource;
  private final ReadWriteLock lock;
  private Future<Void> shutdown;

  public ResourceHook(CloseFuture owner, CloseableResource<R> resource) {
    this.owner = owner;
    this.resource = resource;
    this.lock = new ReentrantReadWriteLock();
  }

  @Override
  public void close(Completable<Void> completion) {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    Future<Void> f;
    try {
      f = shutdown;
      if (f == null) {
        f = resource
          .shutdown(Duration.ZERO);
        shutdown = f;
      }
    } finally {
      writeLock.unlock();
    }
    f.onComplete(completion);
  }

  @Override
  public R get() {
    return resource.get();
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    Future<Void> f;
    try {
      f = shutdown;
      if (f == null) {
        owner.remove(this);
        shutdown = resource.shutdown(timeout);
        f = shutdown;
      }
    } finally {
      writeLock.unlock();
    }
    return f;
  }
}
