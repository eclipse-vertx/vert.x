/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

/**
 * A write operation.
 */
public interface WriteOp {

  /**
   * Perform the write operation.
   */
  void write();

  /**
   * Cancel the write.
   *
   * @param cause the cancellation cause
   */
  void cancel(Throwable cause);

  /**
   * Signal the operation is enqueued.
   */
  void init(int depth, int seq, boolean writable);

  int depth();

  int seq();

  /**
   * Signal the drain, exclusively called when the operation was enqueued as non-writable.
   */
  void onDrain();

  /**
   * Signal the operation will never drain: the stream is closed.
   */
  void onDispose();
}
