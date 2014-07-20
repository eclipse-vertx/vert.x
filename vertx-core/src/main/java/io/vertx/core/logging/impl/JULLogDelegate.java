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

package io.vertx.core.logging.impl;

import io.vertx.core.logging.Logger;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A {@link LogDelegate} which delegates to java.util.logging
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 */
public class JULLogDelegate implements LogDelegate {
  private static final String FQCN = Logger.class.getCanonicalName();

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

  public void fatal(final Object message, final Throwable t) {
    log(Level.SEVERE, message, t);
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
    record.setThrown(t);
    // Find FQCN of calling class
    boolean found = false;
    for (StackTraceElement ste : new Throwable().getStackTrace()) {
      if (FQCN.equals(ste.getClassName())) {
        found = true;
      } else if (found && !FQCN.equals(ste.getClassName())) {
        record.setSourceClassName(ste.getClassName());
        record.setSourceMethodName(ste.getMethodName());
        break;
      }
    }
    // If not found, set it to null, which will default to use logger name. Otherwise it will be incorrect and
    // use this class.
    if (!found) {
      record.setSourceClassName(null);
    }

    logger.log(record);
  }
}
