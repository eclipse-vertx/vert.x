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

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * The result of a write operation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface WriteResult extends Future<Void> {

  /**
   * @return the stream writable status after submission.
   */
  boolean isWritable();

  /**
   * Set a drain handler called when the stream becomes writable again regarding this specific write,
   * it is only effective if and only if the result is not writable.
   *
   * @param handler the handler to be notified
   * @return a reference to this, so it can be used fluently
   */
  WriteResult drainHandler(Handler<Void> handler);

}
