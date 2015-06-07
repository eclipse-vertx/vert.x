/*
 * Copyright (c) 2009 Red Hat, Inc.
 * -------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.logging;

import io.vertx.core.logging.LogFormatter.LogTuple;
import io.vertx.core.spi.logging.LogDelegate;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A {@link io.vertx.core.spi.logging.LogDelegate} which delegates to java.util.logging
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 * @author Patrick Sauts
 */
public class JULLogDelegate implements LogDelegate {
  private final java.util.logging.Logger logger;

  JULLogDelegate(final String name) {
    logger = java.util.logging.Logger.getLogger(name);
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

  public void error(final Object message) {
    log(Level.SEVERE, message);
  }

  public void warn(final Object message) {
    log(Level.WARNING, message);
  }

  public void info(final Object message) {
    log(Level.INFO, message);
  }

  public void debug(final Object message) {
    log(Level.FINE, message);
  }

  public void trace(final Object message) {
    log(Level.FINEST, message);
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
    record.setThrown(t);
    // This will disable stack trace lookup inside JUL. If someone wants location info, they can use their own formatter
    // or use a different logging framework like sl4j, or log4j
    record.setSourceClassName(null);

    logger.log(record);
  }

  @Override
  public void fatal(String format, Object arg) {
    if (logger.isLoggable(Level.SEVERE)) {
      logger.severe(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void fatal(String format, Object arg1, Object arg2) {
    if (logger.isLoggable(Level.SEVERE)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.SEVERE, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void fatal(String format, Object... arguments) {
    if (logger.isLoggable(Level.SEVERE)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.SEVERE, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void error(String format, Object arg) {
    if (logger.isLoggable(Level.SEVERE)) {
      logger.severe(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void error(String format, Object arg1, Object arg2) {
    if (logger.isLoggable(Level.SEVERE)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.SEVERE, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void error(String format, Object... arguments) {
    if (logger.isLoggable(Level.SEVERE)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.SEVERE, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void warn(String format, Object arg) {
    if (logger.isLoggable(Level.WARNING)) {
      logger.warning(LogFormatter.format(format, arg).getMessage());
    }

  }

  @Override
  public void warn(String format, Object arg1, Object arg2) {
    if (logger.isLoggable(Level.WARNING)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.WARNING, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void warn(String format, Object... arguments) {
    if (logger.isLoggable(Level.WARNING)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.WARNING, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void info(String format, Object arg) {
    if (logger.isLoggable(Level.INFO)) {
      logger.info(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void info(String format, Object arg1, Object arg2) {
    if (logger.isLoggable(Level.INFO)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.INFO, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void info(String format, Object... arguments) {
    if (logger.isLoggable(Level.INFO)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.INFO, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void debug(String format, Object arg) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void debug(String format, Object arg1, Object arg2) {
    if (logger.isLoggable(Level.FINE)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.FINE, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void debug(String format, Object... arguments) {
    if (logger.isLoggable(Level.FINE)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.FINE, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void trace(String format, Object arg) {
    if (logger.isLoggable(Level.FINEST)) {
      logger.finest(LogFormatter.format(format, arg).getMessage());
    }

  }

  @Override
  public void trace(String format, Object arg1, Object arg2) {
    if (logger.isLoggable(Level.FINEST)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.FINEST, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void trace(String format, Object... arguments) {
    if (logger.isLoggable(Level.FINEST)) {
      logger.finest(LogFormatter.format(format, arguments).getMessage());
    }
  }
}
