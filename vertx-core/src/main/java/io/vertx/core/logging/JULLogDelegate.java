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

package io.vertx.core.logging;

import io.vertx.core.spi.logging.LogDelegate;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A {@link io.vertx.core.spi.logging.LogDelegate} which delegates to java.util.logging
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 */
public class JULLogDelegate implements LogDelegate {
  private final java.util.logging.Logger logger;

  JULLogDelegate(final String name) {
    logger = java.util.logging.Logger.getLogger(name);
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isLoggable(Level.WARNING);
  }

  public boolean isInfoEnabled() {
    return logger.isLoggable(Level.INFO);
  }

  public boolean isDebugEnabled() {
    return logger.isLoggable(Level.FINE);
  }

  public boolean isTraceEnabled() {
    return logger.isLoggable(Level.FINEST);
  }

  public void error(final Object message) {
    log(Level.SEVERE, message);
  }

  public void error(final Object message, final Throwable t) {
    log(Level.SEVERE, message, t);
  }

  public void warn(final Object message) {
    log(Level.WARNING, message);
  }

  public void warn(final Object message, final Throwable t) {
    log(Level.WARNING, message, t);
  }

  public void info(final Object message) {
    log(Level.INFO, message);
  }

  public void info(final Object message, final Throwable t) {
    log(Level.INFO, message, t);
  }

  public void debug(final Object message) {
    log(Level.FINE, message);
  }

  public void debug(final Object message, final Throwable t) {
    log(Level.FINE, message, t);
  }

  public void trace(final Object message) {
    log(Level.FINEST, message);
  }

  public void trace(final Object message, final Throwable t) {
    log(Level.FINEST, message, t);
  }

  private void log(Level level, Object message) {
    log(level, message, null);
  }

  private void log(Level level, Object message, Throwable t) {
    if (!logger.isLoggable(level)) {
      return;
    }
    String msg = (message == null) ? "NULL" : message.toString();
    LogRecord record = new LogRecord(level, msg);
    record.setLoggerName(logger.getName());
    if (t != null) {
      record.setThrown(t);
    }
    // This will disable stack trace lookup inside JUL. If someone wants location info, they can use their own formatter
    // or use a different logging framework like slf4j, or log4j
    record.setSourceClassName(null);
    logger.log(record);
  }

  @Override
  public Object unwrap() {
    return logger;
  }
}
