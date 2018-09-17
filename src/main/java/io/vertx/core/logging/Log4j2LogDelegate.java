/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.ExtendedLogger;

/**
 * A {@link LogDelegate} which delegates to Apache Log4j 2
 *
 * @author Clement Escoffier - clement@apache.org
 */
public class Log4j2LogDelegate implements LogDelegate {

  final ExtendedLogger logger;
  final static String FQCN = Logger.class.getCanonicalName();

  Log4j2LogDelegate(final String name) {
    logger = (ExtendedLogger) org.apache.logging.log4j.LogManager.getLogger(name);
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
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
    log(Level.ERROR, message.toString(), params);
  }

  public void error(final Object message, final Throwable t) {
    log(Level.ERROR, message, t);
  }

  @Override
  public void error(Object message, Throwable t, Object... params) {
    log(Level.ERROR, message.toString(), t, params);
  }

  public void warn(final Object message) {
    log(Level.WARN, message);
  }

  @Override
  public void warn(Object message, Object... params) {
    log(Level.WARN, message.toString(), params);
  }

  public void warn(final Object message, final Throwable t) {
    log(Level.WARN, message, t);
  }

  @Override
  public void warn(Object message, Throwable t, Object... params) {
    log(Level.WARN, message.toString(), t, params);
  }

  public void info(final Object message) {
    log(Level.INFO, message);
  }

  @Override
  public void info(Object message, Object... params) {
    log(Level.INFO, message.toString(), params);
  }

  public void info(final Object message, final Throwable t) {
    log(Level.INFO, message, t);
  }

  @Override
  public void info(Object message, Throwable t, Object... params) {
    log(Level.INFO, message.toString(), t, params);
  }

  public void debug(final Object message) {
    log(Level.DEBUG, message);
  }

  @Override
  public void debug(Object message, Object... params) {
    log(Level.DEBUG, message.toString(), params);
  }

  public void debug(final Object message, final Throwable t) {
    log(Level.DEBUG, message, t);
  }

  @Override
  public void debug(Object message, Throwable t, Object... params) {
    log(Level.DEBUG, message.toString(), t, params);
  }

  public void trace(final Object message) {
    log(Level.TRACE, message);
  }

  @Override
  public void trace(Object message, Object... params) {
    log(Level.TRACE, message.toString(), params);
  }

  public void trace(final Object message, final Throwable t) {
    log(Level.TRACE, message.toString(), t);
  }

  @Override
  public void trace(Object message, Throwable t, Object... params) {
    log(Level.TRACE, message.toString(), t, params);
  }

  private void log(Level level, Object message) {
    log(level, message, null);
  }

  private void log(Level level, Object message, Throwable t) {
    if (message instanceof Message) {
      logger.logIfEnabled(FQCN, level, null, (Message) message, t);
    } else {
      logger.logIfEnabled(FQCN, level, null, message, t);
    }
  }

  private void log(Level level, String message, Object... params) {
    logger.logIfEnabled(FQCN, level, null, message, params);
  }

  private void log(Level level, String message, Throwable t, Object... params) {
    logger.logIfEnabled(FQCN, level, null, new FormattedMessage(message, params), t);
  }

  @Override
  public Object unwrap() {
    return logger;
  }
}
