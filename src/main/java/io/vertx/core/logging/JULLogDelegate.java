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

  public void fatal(final Object message) {
    log(Level.SEVERE, message);
  }

  public void fatal(final Object message, final Throwable t) {
    log(Level.SEVERE, message, t);
  }

  public void error(final Object message) {
    log(Level.SEVERE, message);
  }

  @Override
  public void error(Object message, Object... params) {
    log(Level.SEVERE, message, null, params);
  }

  public void error(final Object message, final Throwable t) {
    log(Level.SEVERE, message, t);
  }

  @Override
  public void error(Object message, Throwable t, Object... params) {
    log(Level.SEVERE, message, t, params);
  }

  public void warn(final Object message) {
    log(Level.WARNING, message);
  }

  @Override
  public void warn(Object message, Object... params) {
    log(Level.WARNING, message, null, params);
  }

  public void warn(final Object message, final Throwable t) {
    log(Level.WARNING, message, t);
  }

  @Override
  public void warn(Object message, Throwable t, Object... params) {
    log(Level.WARNING, message, t, params);
  }

  public void info(final Object message) {
    log(Level.INFO, message);
  }

  @Override
  public void info(Object message, Object... params) {
    log(Level.INFO, message, null, params);
  }

  public void info(final Object message, final Throwable t) {
    log(Level.INFO, message, t);
  }

  @Override
  public void info(Object message, Throwable t, Object... params) {
    log(Level.INFO, message, t, params);
  }

  public void debug(final Object message) {
    log(Level.FINE, message);
  }

  @Override
  public void debug(Object message, Object... params) {
    log(Level.FINE, message, null, params);
  }

  public void debug(final Object message, final Throwable t) {
    log(Level.FINE, message, t);
  }

  @Override
  public void debug(Object message, Throwable t, Object... params) {
    log(Level.FINE, message, t, params);
  }

  public void trace(final Object message) {
    log(Level.FINEST, message);
  }

  @Override
  public void trace(Object message, Object... params) {
    log(Level.FINEST, message, null, params);
  }

  public void trace(final Object message, final Throwable t) {
    log(Level.FINEST, message, t);
  }

  @Override
  public void trace(Object message, Throwable t, Object... params) {
    log(Level.FINEST, message, t, params);
  }

  private void log(Level level, Object message) {
    log(level, message, null);
  }

  private void log(Level level, Object message, Throwable t, Object... params) {
    if (!logger.isLoggable(level)) {
      return;
    }
    String msg = (message == null) ? "NULL" : message.toString();
    LogRecord record = new LogRecord(level, msg);
    record.setLoggerName(logger.getName());
    if (t != null) {
      record.setThrown(t);
    } else if (params != null && params.length != 0 && params[params.length - 1] instanceof Throwable) {
      // The exception may be the last parameters (SLF4J uses this convention).
      record.setThrown((Throwable) params[params.length - 1]);
    }
    // This will disable stack trace lookup inside JUL. If someone wants location info, they can use their own formatter
    // or use a different logging framework like sl4j, or log4j
    record.setSourceClassName(null);
    record.setParameters(params);
    logger.log(record);
  }

  private void log(Level level, Object message, Throwable t) {
    log(level, message, t, (Object[]) null);
  }

  @Override
  public Object unwrap() {
    return logger;
  }
}
