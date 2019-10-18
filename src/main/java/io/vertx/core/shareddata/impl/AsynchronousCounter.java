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

package io.vertx.core.shareddata.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.Counter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsynchronousCounter implements Counter {

  private final VertxInternal vertx;
  private final AtomicLong counter;

  public AsynchronousCounter(VertxInternal vertx) {
    this.vertx = vertx;
    this.counter = new AtomicLong();
  }

  public AsynchronousCounter(VertxInternal vertx, AtomicLong counter) {
    this.vertx = vertx;
    this.counter = counter;
  }

  @Override
  public Future<Long> get() {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Long> promise = context.promise();
    promise.complete(counter.get());
    return promise.future();
  }

  @Override
  public Future<Long> incrementAndGet() {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Long> promise = context.promise();
    promise.complete(counter.incrementAndGet());
    return promise.future();
  }

  @Override
  public Future<Long> getAndIncrement() {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Long> promise = context.promise();
    promise.complete(counter.getAndIncrement());
    return promise.future();
  }

  @Override
  public Future<Long> decrementAndGet() {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Long> promise = context.promise();
    promise.complete(counter.decrementAndGet());
    return promise.future();
  }

  @Override
  public Future<Long> addAndGet(long value) {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Long> promise = context.promise();
    promise.complete(counter.addAndGet(value));
    return promise.future();
  }

  @Override
  public Future<Long> getAndAdd(long value) {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Long> promise = context.promise();
    promise.complete(counter.getAndAdd(value));
    return promise.future();
  }

  @Override
  public Future<Boolean> compareAndSet(long expected, long value) {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<Boolean> promise = context.promise();
    promise.complete(counter.compareAndSet(expected, value));
    return promise.future();
  }
}
