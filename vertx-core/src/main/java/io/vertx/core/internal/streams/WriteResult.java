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
 * The result of the submission of write operation to a stream.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface WriteResult extends Future<Void> {

  /**
   * @return the writable status of the stream after submission.
   */
  boolean isWritable();

  /**
   * Set a drain handler called when the stream becomes writable again regarding the submission of this result,
   * it is only effective if and only if the result is not writable.
   *
   * <p>A result signals exactly once, either drained (this handler) or disposed ({@link #disposeHandler}): a
   * handler set after the signal was produced is called immediately when the signal matches, otherwise it is
   * never called.
   *
   * @param handler the handler to be notified
   * @return a reference to this, so it can be used fluently
   */
  WriteResult drainHandler(Handler<Void> handler);

  /**
   * Set a dispose handler called when the stream is closed before this result drained: no drain will ever
   * happen and the writer shall stop, the write future carries the outcome of the write itself.
   *
   * <p>A result signals exactly once, either drained ({@link #drainHandler}) or disposed (this handler): a
   * handler set after the signal was produced is called immediately when the signal matches, otherwise it is
   * never called.
   *
   * @param handler the handler to be notified
   * @return a reference to this, so it can be used fluently
   */
  WriteResult disposeHandler(Handler<Void> handler);

}
