/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.context;

import io.vertx.core.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.context.ContextManager;
import java.lang.ref.WeakReference;

public class ContextManagerImpl implements ContextManager {
  private final ThreadLocal<WeakReference<Context>> stickyContext = new ThreadLocal<>();

  @Override
  public Context getContext(Vertx vertx) {
    Context ctx = ContextInternal.current();
    if (ctx != null && ctx.owner() == vertx) {
      return ctx;
    } else {
      WeakReference<Context> ref = stickyContext.get();
      return ref != null ? ref.get() : null;
    }
  }

  @Override
  public void setContext(Context ctx) {
    stickyContext.set(new WeakReference<>(ctx));
    return;
  }
}
