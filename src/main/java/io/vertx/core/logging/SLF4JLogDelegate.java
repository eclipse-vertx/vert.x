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


import io.vertx.core.spi.logging.LogDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import static org.slf4j.spi.LocationAwareLogger.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SLF4JLogDelegate implements LogDelegate {
  private static final String FQCN = io.vertx.core.logging.Logger.class.getCanonicalName();

  private final Logger logger;

  SLF4JLogDelegate(final String name) {
    logger = LoggerFactory.getLogger(name);
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

  public void fatal(final Object message) {
    log(ERROR_INT, message);
  }

  public void fatal(final Object message, final Throwable t) {
    log(ERROR_INT, message, t);
  }

  public void error(final Object message) {
    log(ERROR_INT, message);
  }

  @Override
  public void error(Object message, Object... params) {
    log(ERROR_INT, message, null, params);
  }

  public void error(final Object message, final Throwable t) {
    log(ERROR_INT, message, t);
  }

  @Override
  public void error(Object message, Throwable t, Object... params) {
    log(ERROR_INT, message, t, params);
  }

  public void warn(final Object message) {
    log(WARN_INT, message);
  }

  @Override
  public void warn(Object message, Object... params) {
    log(WARN_INT, message, null, params);
  }

  public void warn(final Object message, final Throwable t) {
    log(WARN_INT, message, t);
  }

  @Override
  public void warn(Object message, Throwable t, Object... params) {
    log(WARN_INT, message, t, params);
  }

  public void info(final Object message) {
    log(INFO_INT, message);
  }

  @Override
  public void info(Object message, Object... params) {
    log(INFO_INT, message, null, params);
  }

  public void info(final Object message, final Throwable t) {
    log(INFO_INT, message, t);
  }

  @Override
  public void info(Object message, Throwable t, Object... params) {
    log(INFO_INT, message, t, params);
  }

  public void debug(final Object message) {
    log(DEBUG_INT, message);
  }

  public void debug(final Object message, final Object... params) {
    log(DEBUG_INT, message, null, params);
  }

  public void debug(final Object message, final Throwable t) {
    log(DEBUG_INT, message, t);
  }

  public void debug(final Object message, final Throwable t, final Object... params) {
    log(DEBUG_INT, message, t, params);
  }

  public void trace(final Object message) {
    log(TRACE_INT, message);
  }

  @Override
  public void trace(Object message, Object... params) {
    log(TRACE_INT, message, null, params);
  }

  public void trace(final Object message, final Throwable t) {
    log(TRACE_INT, message, t);
  }

  @Override
  public void trace(Object message, Throwable t, Object... params) {
    log(TRACE_INT, message, t, params);
  }

  private void log(int level, Object message) {
    log(level, message, null);
  }

  private void log(int level, Object message, Throwable t) {
    log(level, message, t, null);
  }

  private void log(int level, Object message, Throwable t, final Object... params) {
    String msg = (message == null) ? "NULL" : message.toString();

    if (logger instanceof LocationAwareLogger) {
      LocationAwareLogger l = (LocationAwareLogger) logger;
      l.log(null, FQCN, level, msg, null, t);
    } else {
      switch (level) {
        case TRACE_INT:
          logger.trace(msg, t, params);
          break;
        case DEBUG_INT:
          logger.debug(msg, t, params);
          break;
        case INFO_INT:
          logger.info(msg, t, params);
          break;
        case WARN_INT:
          logger.warn(msg, t, params);
          break;
        case ERROR_INT:
          logger.error(msg, t, params);
          break;
        default:
          throw new IllegalArgumentException("Unknown log level " + level);
      }
    }
  }
}
