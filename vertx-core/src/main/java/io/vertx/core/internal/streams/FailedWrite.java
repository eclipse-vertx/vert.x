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
package io.vertx.core.internal.streams;

import io.vertx.core.Handler;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.internal.ContextInternal;

/**
 * A write disposed at birth: the write was never performed and no drain will ever happen. The future
 * is failed with the cause, a dispose handler is always signaled and a drain handler never is.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class FailedWrite extends FailedFuture<Void> implements WriteResult {

  public FailedWrite(ContextInternal context, Throwable cause) {
    super(context, cause);
  }

  public FailedWrite(ContextInternal context, String failureMessage) {
    super(context, failureMessage);
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public WriteResult drainHandler(Handler<Void> handler) {
    return this;
  }

  @Override
  public WriteResult disposeHandler(Handler<Void> handler) {
    if (handler != null) {
      context.runOnContext(handler);
    }
    return this;
  }
}
