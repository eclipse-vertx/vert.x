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

import io.vertx.core.Context;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerHolder<T> {

  public final Context context;
  public final String address;
  public final HandlerRegistration<T> handler;
  public final boolean replyHandler;
  public final boolean localOnly;
  private boolean removed;

  public HandlerHolder(HandlerRegistration<T> handler,
                       String address,
                       boolean replyHandler,
                       boolean localOnly,
                       Context context) {
    this.context = context;
    this.handler = handler;
    this.address = address;
    this.replyHandler = replyHandler;
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

  // Because of biased locks the overhead of the synchronized lock should be very low as it's almost always
  // called by the same event loop
  public synchronized boolean isRemoved() {
    return removed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HandlerHolder that = (HandlerHolder) o;
    if (handler != null ? !handler.equals(that.handler) : that.handler != null) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return handler != null ? handler.hashCode() : 0;
  }

  public Context getContext() {
    return context;
  }

  public HandlerRegistration<T> getHandler() {
    return handler;
  }

  public boolean isReplyHandler() {
    return replyHandler;
  }

  public boolean isLocalOnly() {
    return localOnly;
  }
;
}
