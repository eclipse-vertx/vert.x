/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.vertx.core.impl.ContextInternal;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerHolder<T> {
  public final ContextInternal context;
  public final T handler;

  public HandlerHolder(ContextInternal context, T handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HandlerHolder that = (HandlerHolder) o;

    if (context != that.context) return false;
    if (handler != null ? !handler.equals(that.handler) : that.handler != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = context.hashCode();
    result = 31 * result + handler.hashCode();
    return result;
  }
}
