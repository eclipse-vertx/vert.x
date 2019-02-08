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

package io.vertx.core.impl.logging;

import io.vertx.core.spi.logging.LogDelegate;

/**
 * @author Thomas Segismont
 */
public final class LoggerAdapter implements Logger {

  private final LogDelegate adapted;

  LoggerAdapter(LogDelegate adapted) {
    this.adapted = adapted;
  }

  @Override
  public boolean isTraceEnabled() {
    return adapted.isTraceEnabled();
  }

  @Override
  public void trace(Object message) {
    adapted.trace(message);
  }

  @Override
  public void trace(Object message, Throwable t) {
    adapted.trace(message, t);
  }

  @Override
  public boolean isDebugEnabled() {
    return adapted.isDebugEnabled();
  }

  @Override
  public void debug(Object message) {
    adapted.debug(message);
  }

  @Override
  public void debug(Object message, Throwable t) {
    adapted.debug(message, t);
  }

  @Override
  public boolean isInfoEnabled() {
    return adapted.isInfoEnabled();
  }

  @Override
  public void info(Object message) {
    adapted.info(message);
  }

  @Override
  public void info(Object message, Throwable t) {
    adapted.info(message, t);
  }

  @Override
  public boolean isWarnEnabled() {
    return adapted.isWarnEnabled();
  }

  @Override
  public void warn(Object message) {
    adapted.warn(message);
  }

  @Override
  public void warn(Object message, Throwable t) {
    adapted.warn(message, t);
  }

  @Override
  public void error(Object message) {
    adapted.error(message);
  }

  @Override
  public void error(Object message, Throwable t) {
    adapted.error(message, t);
  }
}
