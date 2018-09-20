/*
 * Copyright (c) 2009 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.logging;

/**
 * I represent operations that are delegated to underlying logging frameworks.
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 */
public interface LogDelegate {

  boolean isWarnEnabled();

  boolean isInfoEnabled();

  boolean isDebugEnabled();

  boolean isTraceEnabled();

  void fatal(Object message);

  void fatal(Object message, Throwable t);

  void error(Object message);

  void error(Object message, Object... params);

  void error(Object message, Throwable t);

  void error(Object message, Throwable t, Object... params);

  void warn(Object message);

  void warn(Object message, Object... params);

  void warn(Object message, Throwable t);

  void warn(Object message, Throwable t, Object... params);

  void info(Object message);

  void info(Object message, Object... params);

  void info(Object message, Throwable t);

  void info(Object message, Throwable t, Object... params);

  void debug(Object message);

  void debug(Object message, Object... params);

  void debug(Object message, Throwable t);

  void debug(Object message, Throwable t, Object... params);

  void trace(Object message);

  void trace(Object message, Object... params);

  void trace(Object message, Throwable t);

  void trace(Object message, Throwable t, Object... params);

  /**
   * @return the underlying framework logger object, null in the default implementation
   */
  default Object unwrap() {
    return null;
  }
}
