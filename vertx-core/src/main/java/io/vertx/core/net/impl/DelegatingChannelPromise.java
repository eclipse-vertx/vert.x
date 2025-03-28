/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.AbstractFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.Promise;
import io.vertx.core.impl.future.FutureBase;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class DelegatingChannelPromise extends AbstractFuture<Void> implements ChannelPromise {

  private ChannelPromise bridge;
  private final Channel channel;
  private final Promise<Void> promise;

  DelegatingChannelPromise(Promise<Void> promise, Channel channel) {
    this.channel = Objects.requireNonNull(channel);
    this.promise = Objects.requireNonNull(promise);
  }

  @Override
  public Channel channel() {
    return channel;
  }

  @Override
  public ChannelPromise setSuccess(Void result) {
    return setSuccess();
  }

  @Override
  public ChannelPromise setSuccess() {
    promise.succeed();
    return this;
  }

  @Override
  public boolean trySuccess() {
    return promise.tryComplete();
  }

  @Override
  public ChannelPromise setFailure(Throwable cause) {
    promise.tryFail(cause);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
    bridge().addListeners(listener);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ChannelPromise addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
    bridge().addListeners(listeners);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
    bridge().removeListeners(listener);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ChannelPromise removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
    bridge().removeListeners(listeners);
    return this;
  }

  @Override
  public ChannelPromise sync() throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChannelPromise syncUninterruptibly() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChannelPromise await() throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChannelPromise awaitUninterruptibly() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChannelPromise unvoid() {
    return this;
  }

  @Override
  public boolean isVoid() {
    return false;
  }

  @Override
  public boolean trySuccess(Void result) {
    return promise.tryComplete();
  }

  @Override
  public boolean tryFailure(Throwable cause) {
    return promise.tryFail(cause);
  }

  @Override
  public boolean setUncancellable() {
    return true;
  }

  @Override
  public boolean isSuccess() {
    return promise.future().succeeded();
  }

  @Override
  public boolean isCancellable() {
    return false;
  }

  @Override
  public Throwable cause() {
    return promise.future().cause();
  }

  @Override
  public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean await(long timeoutMillis) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean awaitUninterruptibly(long timeoutMillis) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Void getNow() {
    return null;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return promise.future().isComplete();
  }

  private ChannelPromise bridge() {
    ChannelPromise p;
    // Could use double-checked locking ?
    synchronized (this) {
      p = bridge;
      if (p == null) {
        ChannelPromise pr = channel.newPromise();
        p = pr;
        ((FutureBase<?>)promise.future())
          .addListener((result, failure) -> {
          if (failure == null) {
            pr.setSuccess();
          } else {
            pr.setFailure(failure);
          }
        });
        bridge = p;
      }
    }
    return p;
  }
}
