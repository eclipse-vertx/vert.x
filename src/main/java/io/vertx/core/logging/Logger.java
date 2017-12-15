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

/**
 * This class allows us to isolate all our logging dependencies in one place. It also allows us to have zero runtime
 * 3rd party logging jar dependencies, since we default to JUL by default.
 * <p>
 * By default logging will occur using JUL (Java-Util-Logging). The logging configuration file (logging.properties)
 * used by JUL will taken from the default logging.properties in the JDK installation if no {@code  java.util.logging.config.file} system
 * property is set.
 * <p>
 * If you would prefer to use Log4J or SLF4J instead of JUL then you can set a system property called
 * {@code vertx.logger-delegate-factory-class-name} to the class name of the delegate for your logging system.
 * For Log4J the value is {@code io.vertx.core.logging.Log4jLogDelegateFactory}, for SLF4J the value
 * is {@code io.vertx.core.logging.SLF4JLogDelegateFactory}. You will need to ensure whatever jar files
 * required by your favourite log framework are on your classpath.
 * <p>
 * Keep in mind that logging backends use different formats to represent replaceable tokens in parameterized messages.
 * As a consequence, if you rely on parameterized logging methods, you won't be able to switch backends without changing your code.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
public class Logger {

  final LogDelegate delegate;

  public Logger(final LogDelegate delegate) {
    this.delegate = delegate;
  }

  public boolean isInfoEnabled() {
    return delegate.isInfoEnabled();
  }

  public boolean isDebugEnabled() {
    return delegate.isDebugEnabled();
  }

  public boolean isTraceEnabled() {
    return delegate.isTraceEnabled();
  }

  public void fatal(final Object message) {
    delegate.fatal(message);
  }

  public void fatal(final Object message, final Throwable t) {
    delegate.fatal(message, t);
  }

  public void error(final Object message) {
    delegate.error(message);
  }

  public void error(final Object message, final Throwable t) {
    delegate.error(message, t);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void error(final Object message, final Object... objects) {
    delegate.error(message, objects);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void error(final Object message, final Throwable t, final Object... objects) {
    delegate.error(message, t, objects);
  }

  public void warn(final Object message) {
    delegate.warn(message);
  }

  public void warn(final Object message, final Throwable t) {
    delegate.warn(message, t);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void warn(final Object message, final Object... objects) {
    delegate.warn(message, objects);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void warn(final Object message, final Throwable t, final Object... objects) {
    delegate.warn(message, t, objects);
  }

  public void info(final Object message) {
    delegate.info(message);
  }

  public void info(final Object message, final Throwable t) {
    delegate.info(message, t);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void info(final Object message, final Object... objects) {
    delegate.info(message, objects);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void info(final Object message, final Throwable t, final Object... objects) {
    delegate.info(message, t, objects);
  }

  public void debug(final Object message) {
    delegate.debug(message);
  }

  public void debug(final Object message, final Throwable t) {
    delegate.debug(message, t);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void debug(final Object message, final Object... objects) {
    delegate.debug(message, objects);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void debug(final Object message, final Throwable t, final Object... objects) {
    delegate.debug(message, t, objects);
  }

  public void trace(final Object message) {
    delegate.trace(message);
  }

  public void trace(final Object message, final Throwable t) {
    delegate.trace(message, t);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void trace(final Object message, final Object... objects) {
    delegate.trace(message, objects);
  }

  /**
   * @throws UnsupportedOperationException if the logging backend does not support parameterized messages
   */
  public void trace(final Object message, final Throwable t, final Object... objects) {
    delegate.trace(message, t, objects);
  }

  /**
   * @return the delegate instance sending operations to the underlying logging framework
   */
  public LogDelegate getDelegate() {
    return delegate;
  }
}
