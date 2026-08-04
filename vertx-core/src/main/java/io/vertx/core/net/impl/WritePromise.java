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
package io.vertx.core.net.impl;

import io.vertx.core.impl.future.PromiseImpl;
import io.vertx.core.internal.ContextInternal;

/**
 * A write promise.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class WritePromise extends PromiseImpl<Void> implements MessageWrite {

  public WritePromise(ContextInternal context) {
    super(context);
  }

  @Override
  public void cancel(Throwable cause) {
    fail(cause);
  }
}
