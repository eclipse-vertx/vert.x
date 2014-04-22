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

package org.vertx.java.core.logging;

import org.vertx.java.core.logging.impl.LogDelegate;

/**
 * <p>This class allows us to isolate all our logging dependencies in one place. It also allows us to have zero runtime
 * 3rd party logging jar dependencies, since we default to JUL by default.</p>
 *
 * <p>By default logging will occur using JUL (Java-Util-Logging). The logging configuration file (logging.properties)
 * used by JUL will taken from the default logging.properties in the JDK installation if no {@code  java.util.logging.config.file} system
 * property is set. The {@code vertx-java / vertx-ruby / etc} scripts set {@code  java.util.logging.config.file} to point at the logging.properties
 * in the vertx distro install directory. This in turn configures vertx to log to a file in a directory called
 * vertx-logs in the users home directory.</p>
 *
 * <p>If you would prefer to use Log4J or SLF4J instead of JUL then you can set a system property called
 * {@code org.vertx.logger-delegate-factory-class-name} to the class name of the delegate for your logging system.
 * For Log4J the value is {@code org.vertx.java.core.logging.impl.Log4JLogDelegateFactory}, for SLF4J the value
 * is {@code org.vertx.java.core.logging.impl.SLF4JLogDelegateFactory}. You will need to ensure whatever jar files
 * required by your favourite log framework are on your classpath.</p>
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

  public void warn(final Object message) {
    delegate.warn(message);
  }

  public void warn(final Object message, final Throwable t) {
    delegate.warn(message, t);
  }

  public void info(final Object message) {
    delegate.info(message);
  }

  public void info(final Object message, final Throwable t) {
    delegate.info(message, t);
  }

  public void debug(final Object message) {
    delegate.debug(message);
  }

  public void debug(final Object message, final Throwable t) {
    delegate.debug(message, t);
  }

  public void trace(final Object message) {
    delegate.trace(message);
  }

  public void trace(final Object message, final Throwable t) {
    delegate.trace(message, t);
  }

}
