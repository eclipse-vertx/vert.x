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

import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.concurrent.OutboundWriteQueue;
import io.vertx.core.internal.concurrent.WriteOp;

/**
 * An outbound write queue accepting {@link WritePromise} writes.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class OutboundWriteStream extends OutboundWriteQueue<WriteOp> {

  public OutboundWriteStream(EventExecutor consumer) {
    super(consumer);
  }

  public OutboundWriteStream(EventExecutor consumer, int lowWaterMark, int highWaterMark) {
    super(consumer, lowWaterMark, highWaterMark);
  }

  public void add(WritePromise write) {
    add(write.op);
  }
}
