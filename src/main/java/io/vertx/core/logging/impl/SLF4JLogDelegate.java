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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import static org.slf4j.spi.LocationAwareLogger.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SLF4JLogDelegate implements LogDelegate{
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

  public void error(final Object message, final Throwable t) {
    log(ERROR_INT, message, t);
  }

  public void warn(final Object message) {
    log(WARN_INT, message);
  }

  public void warn(final Object message, final Throwable t) {
    log(WARN_INT, message, t);
  }

  public void info(final Object message) {
    log(INFO_INT, message);
  }

  public void info(final Object message, final Throwable t) {
    log(INFO_INT, message, t);
  }

  public void debug(final Object message) {
    log(DEBUG_INT, message);
  }

  public void debug(final Object message, final Throwable t) {
    log(DEBUG_INT, message, t);
  }

  public void trace(final Object message) {
    log(TRACE_INT, message);
  }

  public void trace(final Object message, final Throwable t) {
    log(TRACE_INT, message, t);
  }

  private void log(int level, Object message) {
    log(level, message, null);
  }

  private void log(int level, Object message, Throwable t) {
    String msg = (message == null) ? "NULL" : message.toString();

    if (logger instanceof LocationAwareLogger) {
      LocationAwareLogger l = (LocationAwareLogger) logger;
      l.log(null, FQCN, level, msg, null, t);
    } else {
      switch (level) {
        case TRACE_INT:
          logger.trace(msg, t);
          break;
        case DEBUG_INT:
          logger.debug(msg, t);
          break;
        case INFO_INT:
          logger.info(msg, t);
          break;
        case WARN_INT:
          logger.warn(msg, t);
          break;
        case ERROR_INT:
          logger.error(msg, t);
          break;
        default:
          throw new IllegalArgumentException("Unknown log level " + level);
      }
    }
  }
}
