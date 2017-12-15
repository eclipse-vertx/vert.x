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
import org.apache.log4j.Level;

/**
 * A {@link io.vertx.core.spi.logging.LogDelegate} which delegates to Apache Log4j
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
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

  public void fatal(final Object message) {
    log(Level.FATAL, message);
  }

  public void fatal(final Object message, final Throwable t) {
    log(Level.FATAL, message, t);
  }

  public void error(final Object message) {
    log(Level.ERROR, message);
  }

  @Override
  public void error(Object message, Object... params) {
    throwUnsupportedOperationException();
  }

  public void error(final Object message, final Throwable t) {
    log(Level.ERROR, message, t);
  }

  @Override
  public void error(Object message, Throwable t, Object... params) {
    throwUnsupportedOperationException();
  }

  public void warn(final Object message) {
    log(Level.WARN, message);
  }

  @Override
  public void warn(Object message, Object... params) {
    throwUnsupportedOperationException();
  }

  public void warn(final Object message, final Throwable t) {
    log(Level.WARN, message, t);
  }

  @Override
  public void warn(Object message, Throwable t, Object... params) {
    throwUnsupportedOperationException();
  }

  public void info(final Object message) {
    log(Level.INFO, message);
  }

  @Override
  public void info(Object message, Object... params) {
    throwUnsupportedOperationException();
  }

  public void info(final Object message, final Throwable t) {
    log(Level.INFO, message, t);
  }

  @Override
  public void info(Object message, Throwable t, Object... params) {
    throwUnsupportedOperationException();
  }

  public void debug(final Object message) {
    log(Level.DEBUG, message);
  }

  @Override
  public void debug(Object message, Object... params) {
    throwUnsupportedOperationException();
  }

  public void debug(final Object message, final Throwable t) {
    log(Level.DEBUG, message, t);
  }

  @Override
  public void debug(Object message, Throwable t, Object... params) {
    throwUnsupportedOperationException();
  }

  public void trace(final Object message) {
    log(Level.TRACE, message);
  }

  @Override
  public void trace(Object message, Object... params) {
    throwUnsupportedOperationException();
  }

  public void trace(final Object message, final Throwable t) {
    log(Level.TRACE, message, t);
  }

  @Override
  public void trace(Object message, Throwable t, Object... params) {
    throwUnsupportedOperationException();
  }

  private void log(Level level, Object message) {
    log(level, message, null);
  }

  private void log(Level level, Object message, Throwable t) {
    logger.log(FQCN, level, message, t);
  }

  private void throwUnsupportedOperationException() {
    throw new UnsupportedOperationException("Log4j version used in Vertx doesn't support parameterized logging.");
  }

  @Override
  public Object unwrap() {
    return logger;
  }
}
