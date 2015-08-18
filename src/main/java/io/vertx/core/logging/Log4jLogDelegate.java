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
import org.apache.log4j.Level;

/**
 * A {@link io.vertx.core.spi.logging.LogDelegate} which delegates to Apache Log4j
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 * @author Patrick Sauts
 */
public class Log4jLogDelegate implements LogDelegate {

  private static final String FQCN = Logger.class.getCanonicalName();

  private final org.apache.log4j.Logger logger;

  Log4jLogDelegate(final String name) {
    logger = org.apache.log4j.Logger.getLogger(name);
  }

  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  @Override
  public void fatal(final Object message) {
    log(Level.FATAL, message);
  }

  @Override
  public void error(final Object message) {
    log(Level.ERROR, message);
  }

  @Override
  public void warn(final Object message) {
    log(Level.WARN, message);
  }

  @Override
  public void info(final Object message) {
    log(Level.INFO, message);
  }

  @Override
  public void debug(final Object message) {
    log(Level.DEBUG, message);
  }

  @Override
  public void trace(final Object message) {
    log(Level.TRACE, message);
  }

  private void log(Level level, Object message) {
    log(level, message, null);
  }

  private void log(Level level, Object message, Throwable t) {
    logger.log(FQCN, level, message, t);
  }

  @Override
  public void fatal(String format, Object arg) {
    if (logger.isEnabledFor(Level.FATAL)) {
      logger.fatal(LogFormatter.format(format, arg).getMessage());
    }
    if (logger.isEnabledFor(Level.FATAL)) {
      logger.fatal(LogFormatter.format(format, arg).getMessage());
    }

  }

  @Override
  public void fatal(String format, Object arg1, Object arg2) {
    if (logger.isEnabledFor(Level.FATAL)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.FATAL, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void fatal(String format, Object... arguments) {
    if (logger.isEnabledFor(Level.FATAL)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.FATAL, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void error(String format, Object arg) {
    if (logger.isEnabledFor(Level.ERROR)) {
      logger.error(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void error(String format, Object arg1, Object arg2) {
    if (logger.isEnabledFor(Level.ERROR)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.ERROR, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void error(String format, Object... arguments) {
    if (logger.isEnabledFor(Level.ERROR)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.ERROR, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void warn(String format, Object arg) {
    if (logger.isEnabledFor(Level.WARN)) {
      logger.warn(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void warn(String format, Object arg1, Object arg2) {
    if (logger.isEnabledFor(Level.WARN)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.WARN, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void warn(String format, Object... arguments) {
    if (logger.isEnabledFor(Level.WARN)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.WARN, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void info(String format, Object arg) {
    if (logger.isEnabledFor(Level.INFO)) {
      logger.info(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void info(String format, Object arg1, Object arg2) {
    if (logger.isEnabledFor(Level.INFO)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.INFO, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void info(String format, Object... arguments) {
    if (logger.isEnabledFor(Level.INFO)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.INFO, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void debug(String format, Object arg) {
    if (logger.isEnabledFor(Level.DEBUG)) {
      logger.debug(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void debug(String format, Object arg1, Object arg2) {
    if (logger.isEnabledFor(Level.DEBUG)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.DEBUG, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void debug(String format, Object... arguments) {
    if (logger.isEnabledFor(Level.DEBUG)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.DEBUG, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void trace(String format, Object arg) {
    if (logger.isEnabledFor(Level.TRACE)) {
      logger.trace(LogFormatter.format(format, arg).getMessage());
    }
  }

  @Override
  public void trace(String format, Object arg1, Object arg2) {
    if (logger.isEnabledFor(Level.TRACE)) {
      LogTuple tuple = LogFormatter.format(format, arg1, arg2);
      log(Level.TRACE, tuple.getMessage(), tuple.getThrowable());
    }
  }

  @Override
  public void trace(String format, Object... arguments) {
    if (logger.isEnabledFor(Level.TRACE)) {
      LogTuple tuple = LogFormatter.format(format, arguments);
      log(Level.TRACE, tuple.getMessage(), tuple.getThrowable());
    }
  }
}
