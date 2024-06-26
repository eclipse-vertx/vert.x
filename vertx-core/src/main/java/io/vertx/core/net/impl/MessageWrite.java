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
 * A message write operation.
 */
@FunctionalInterface
public interface MessageWrite {

  /**
   * Write the message.
   */
  void write();

  /**
   * Cancel the write operation.
   *
   * @param cause the cancellation cause
   */
  default void cancel(Throwable cause) {
  }

}
