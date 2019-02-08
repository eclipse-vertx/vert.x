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

package io.vertx.core.impl.logging;

/**
 * <strong>For internal logging purposes only</strong>.
 *
 * @author Thomas Segismont
 */
@SuppressWarnings("deprecation")
public class LoggerFactory {

  /**
   * Like {@link #getLogger(String)}, using the provided {@code clazz} name.
   */
  public static Logger getLogger(Class<?> clazz) {
    return new LoggerAdapter(io.vertx.core.logging.LoggerFactory.getLogger(clazz).getDelegate());
  }

  /**
   * Get the logger with the specified {@code name}.
   */
  public static Logger getLogger(String name) {
    return new LoggerAdapter(io.vertx.core.logging.LoggerFactory.getLogger(name).getDelegate());
  }
}
