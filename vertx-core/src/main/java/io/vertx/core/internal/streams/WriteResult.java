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
 * The result of the submission of a write operation to a stream.
 *
 * <p>The result is a future completed with the outcome of the write. In addition it carries the writable
 * status of the stream observed at submission and a one-shot signal: when the result is not writable, it
 * either drains ({@link #drainHandler}) when the stream becomes writable again regarding this write, or is
 * disposed ({@link #disposeHandler}) when the stream is closed before that happens.
 *
 * <p>At most one handler of each kind can be set: setting a handler while one is set throws an
 * {@link IllegalStateException}, setting a {@code null} handler unsets a handler that has not been
 * called yet.
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
