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

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.eventbus.impl.HandlerHolder;
import io.vertx.core.eventbus.impl.HandlerRegistration;
import io.vertx.core.impl.ContextInternal;

public class ClusteredHandlerHolder<T> extends HandlerHolder<T> {

  private final long seq;

  public ClusteredHandlerHolder(HandlerRegistration<T> handler, boolean localOnly, ContextInternal context, long seq) {
    super(handler, localOnly, context);
    this.seq = seq;
  }

  @Override
  public long getSeq() {
    return seq;
  }
}
