/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SLF4JLogDelegate implements LogDelegate {

  private final Logger logger;

  SLF4JLogDelegate(final String name) {
    logger = LoggerFactory.getLogger(name);
  }

  // Visible for testing
  public SLF4JLogDelegate(Object logger) {
    this.logger = (Logger) logger;
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  @Override
  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override
  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  @Override
  public void error(final Object message) {
    logger.error(valueOf(message));
  }

  @Override
  public void error(final Object message, final Throwable t) {
    logger.error(valueOf(message), t);
  }

  @Override
  public void warn(final Object message) {
    logger.warn(valueOf(message));
  }

  @Override
  public void warn(final Object message, final Throwable t) {
    logger.warn(valueOf(message), t);
  }

  @Override
  public void info(final Object message) {
    logger.info(valueOf(message));
  }

  @Override
  public void info(final Object message, final Throwable t) {
    logger.info(valueOf(message), t);
  }

  @Override
  public void debug(final Object message) {
    logger.debug(valueOf(message));
  }

  @Override
  public void debug(final Object message, final Throwable t) {
    logger.debug(valueOf(message), t);
  }

  @Override
  public void trace(final Object message) {
    logger.trace(valueOf(message));
  }

  @Override
  public void trace(final Object message, final Throwable t) {
    logger.trace(valueOf(message), t);
  }

  private static String valueOf(Object message) {
    return message == null ? "NULL" : message.toString();
  }

  @Override
  public Object unwrap() {
    return logger;
  }
}
