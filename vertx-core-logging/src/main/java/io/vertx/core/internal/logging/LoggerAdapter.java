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

package io.vertx.core.internal.logging;

import io.vertx.core.spi.logging.LogDelegate;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Thomas Segismont
 */
public final class LoggerAdapter implements Logger {

  private static volatile Consumer<Throwable> loggerFailureHandler;

  /**
   * Set a JVM wide handler that gets reported exception thrown by actual logger implementations.
   *
   * @param handler the handler getting reported failures
   */
  public static void setLoggerFailureHandler(Consumer<Throwable> handler) {
    loggerFailureHandler = handler;
  }

  private final LogDelegate adapted;

  // Visible for testing
  public LoggerAdapter(LogDelegate adapted) {
    this.adapted = Objects.requireNonNull(adapted);
  }

  private static void reportLoggerFailure(Throwable t) {
    Consumer<Throwable> handler = loggerFailureHandler;
    if (handler != null) {
      try {
        handler.accept(t);
      } catch (Throwable ignore) {
      }
    }
  }

  @Override
  public String implementation() {
    try {
      return adapted.implementation();
    } catch (Exception e) {
      reportLoggerFailure(e);
      return "Unknown";
    }
  }

  @Override
  public boolean isTraceEnabled() {
    try {
      return adapted.isTraceEnabled();
    } catch (Exception e) {
      reportLoggerFailure(e);
      return false;
    }
  }

  @Override
  public void trace(Object message) {
    try {
      adapted.trace(message);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public void trace(Object message, Throwable t) {
    try {
      adapted.trace(message, t);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public boolean isDebugEnabled() {
    try {
      return adapted.isDebugEnabled();
    } catch (Exception e) {
      reportLoggerFailure(e);
      return false;
    }
  }

  @Override
  public void debug(Object message) {
    try {
      adapted.debug(message);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public void debug(Object message, Throwable t) {
    try {
      adapted.debug(message, t);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public boolean isInfoEnabled() {
    try {
      return adapted.isInfoEnabled();
    } catch (Exception e) {
      reportLoggerFailure(e);
      return false;
    }
  }

  @Override
  public void info(Object message) {
    try {
      adapted.info(message);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public void info(Object message, Throwable t) {
    try {
      adapted.info(message, t);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public boolean isWarnEnabled() {
    try {
      return adapted.isWarnEnabled();
    } catch (Exception e) {
      reportLoggerFailure(e);
      return false;
    }
  }

  @Override
  public void warn(Object message) {
    try {
      adapted.warn(message);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public void warn(Object message, Throwable t) {
    try {
      adapted.warn(message, t);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public void error(Object message) {
    try {
      adapted.error(message);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  @Override
  public void error(Object message, Throwable t) {
    try {
      adapted.error(message, t);
    } catch (Exception e) {
      reportLoggerFailure(e);
    }
  }

  public LogDelegate unwrap() {
    return adapted;
  }
}
