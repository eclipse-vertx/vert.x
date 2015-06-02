/*
 * Copyright (c) 2009 Red Hat, Inc. ------------------------------------- All rights reserved. This program and the
 * accompanying materials are made available under the terms of the Eclipse Public License v1.0 and Apache License v2.0
 * which accompanies this distribution. The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php You may elect to redistribute this code under either of these
 * licenses.
 */

package io.vertx.core.logging;

import io.vertx.core.logging.impl.LogDelegate;

/**
 * This class allows us to isolate all our logging dependencies in one place. It also allows us to have zero runtime 3rd
 * party logging jar dependencies, since we default to JUL by default.
 * <p>
 * By default logging will occur using JUL (Java-Util-Logging). The logging configuration file (logging.properties) used
 * by JUL will taken from the default logging.properties in the JDK installation if no
 * {@code  java.util.logging.config.file} system property is set.
 * <p>
 * If you would prefer to use Log4J or SLF4J instead of JUL then you can set a system property called
 * {@code io.vertx.logger-delegate-factory-class-name} to the class name of the delegate for your logging system. For
 * Log4J the value is {@code io.vertx.core.logging.impl.Log4JLogDelegateFactory}, for SLF4J the value is
 * {@code io.vertx.core.logging.impl.SLF4JLogDelegateFactory}. You will need to ensure whatever jar files required by
 * your favourite log framework are on your classpath.
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

  public void fatal(String format, Object arg) {
    delegate.fatal(format, arg);
  }

  public void fatal(String format, Object arg1, Object arg2) {
    delegate.fatal(format, arg1, arg2);
  }

  public void fatal(String format, Object... arguments) {
    delegate.fatal(format, arguments);
  }

  public void error(final Object message) {
    delegate.error(message);
  }

  public void error(String format, Object arg) {
    delegate.error(format, arg);
  }

  public void error(String format, Object arg1, Object arg2) {
    delegate.error(format, arg1, arg2);
  }

  public void error(String format, Object... arguments) {
    delegate.error(format, arguments);
  }

  public void warn(final Object message) {
    delegate.warn(message);
  }

  public void warn(String format, Object arg) {
    delegate.warn(format, arg);
  }

  public void warn(String format, Object arg1, Object arg2) {
    delegate.warn(format, arg1, arg2);
  }

  public void warn(String format, Object... arguments) {
    delegate.warn(format, arguments);
  }

  public void info(final Object message) {
    delegate.info(message);
  }

  public void info(String format, Object arg) {
    delegate.info(format, arg);
  }

  public void info(String format, Object arg1, Object arg2) {
    delegate.info(format, arg1, arg2);
  }

  public void info(String format, Object... arguments) {
    delegate.info(format, arguments);
  }

  public void debug(final Object message) {
    delegate.debug(message);
  }

  public void debug(String format, Object arg) {
    delegate.debug(format, arg);
  }

  public void debug(String format, Object arg1, Object arg2) {
    delegate.debug(format, arg1, arg2);
  }

  public void debug(String format, Object... arguments) {
    delegate.debug(format, arguments);
  }

  public void trace(final Object message) {
    delegate.trace(message);
  }

  public void trace(String format, Object arg) {
    delegate.trace(format, arg);
  }

  public void trace(String format, Object arg1, Object arg2) {
    delegate.trace(format, arg1, arg2);
  }

  public void trace(String format, Object... arguments) {
    delegate.trace(format, arguments);
  }
}
