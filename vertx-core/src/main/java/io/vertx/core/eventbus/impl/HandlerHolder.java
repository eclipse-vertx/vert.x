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

package io.vertx.core.eventbus.impl;

import io.vertx.core.internal.ContextInternal;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerHolder<T> {

  public final ContextInternal context;
  public final HandlerRegistration<T> handler;
  public final boolean localOnly;
  private boolean removed;

  public HandlerHolder(HandlerRegistration<T> handler, boolean localOnly, ContextInternal context) {
    this.context = context;
    this.handler = handler;
    this.localOnly = localOnly;
  }

  // We use a synchronized block to protect removed as it can be unregistered from a different thread
  boolean setRemoved() {
    boolean unregistered = false;
    synchronized (this) {
      if (!removed) {
        removed = true;
        unregistered = true;
      }
    }
    return unregistered;
  }

  public synchronized boolean isRemoved() {
    return removed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HandlerHolder<?> that = (HandlerHolder<?>) o;
    return Objects.equals(handler, that.handler);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(handler);
  }

  public long getSeq() {
    return 0;
  }

  public ContextInternal getContext() {
    return context;
  }

  public HandlerRegistration<T> getHandler() {
    return handler;
  }

  public boolean isLocalOnly() {
    return localOnly;
  }
}
